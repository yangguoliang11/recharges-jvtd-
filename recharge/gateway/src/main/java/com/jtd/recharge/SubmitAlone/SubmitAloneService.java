package com.jtd.recharge.SubmitAlone;

import com.alibaba.fastjson.JSON;
import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import com.jtd.recharge.base.cache.redis.RedisTemplate;
import com.jtd.recharge.base.constant.SysConstants;
import com.jtd.recharge.base.util.CommonUtil;
import com.jtd.recharge.base.util.MessageClient;
import com.jtd.recharge.bean.ChargeMessage;
import com.jtd.recharge.bean.ChargeRequest;
import com.jtd.recharge.bean.ChargeSubmitResponse;
import com.jtd.recharge.connect.base.ChargeAdapter;
import com.jtd.recharge.connect.base.ChargeAdapterList;
import com.jtd.recharge.dao.bean.CallbackReport;
import com.jtd.recharge.dao.mapper.ChargeOrderDetailMapper;
import com.jtd.recharge.dao.mapper.ChargeOrderMapper;
import com.jtd.recharge.dao.po.ChargeOrder;
import com.jtd.recharge.dao.po.ChargeOrderDetail;
import com.jtd.recharge.dao.po.User;
import com.jtd.recharge.sender.LoadBalance;
import com.jtd.recharge.service.charge.order.PushCallbackService;
import com.jtd.recharge.service.user.BalanceService;
import com.jtd.recharge.service.user.UserService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.annotation.ThreadSafe;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/8/26.
 */
@Service("submitAloneService")
public class SubmitAloneService {
    private Log log = LogFactory.getLog(this.getClass());

    @Resource
    ChargeOrderMapper chargeOrderMapper;

    @Resource
    ChargeOrderDetailMapper chargeOrderDetailMapper;

    @Resource
    ChargeAdapterList chargeAdapterList;

    @Resource
    BalanceService balanceService;

    @Resource
    PushCallbackService pushCallbackService;

    @Resource
    UserService userService;
    @Resource(name="commRedisTemplate")
    RedisTemplate redisTemplate;

    public String chargeSend(List<ChargeMessage> chargeMessageList) {
        try {
            long startReportHandletime = System.currentTimeMillis();//程序开始时长
            List<ChargeRequest> chargeRequestList=new ArrayList<>();
            for(int i=0;i<chargeMessageList.size();i++) {
                ChargeMessage chargeMessage = chargeMessageList.get(i);
                List<ChargeRequest> supplyList = chargeMessage.getSupplyList();
                ChargeRequest chargeRequests = supplyList.get(0);
                String orderNum = chargeMessage.getOrderNum();
                log.info("7、订单进入发送流程----mobile =" + chargeRequests.getMobile() + " orderNum=" + orderNum);

                String channelNum = CommonUtil.getChannelNum();//流水

                log.info("7、订单进入发送流程----mobile =" + chargeRequests.getMobile() + " orderNum=" + orderNum + "=========供应商SupplyName:" + chargeRequests.getSupplyName());
                chargeRequests.setChannelNum(channelNum);//设置流水
                chargeRequests.setOrderNum(orderNum);//设置流水

                chargeRequestList.add(chargeRequests);
                }
                ChargeSubmitResponse chargeSubmitResponse = chargeAdapterList.chargeRequest(chargeRequestList);
                log.info("提交湖北流量发送执行时长:"+(System.currentTimeMillis()-startReportHandletime));
                 //添加订单明细
                addOrderDetail(1,chargeRequestList, chargeSubmitResponse);

                List listOrderNum=new ArrayList();
                List listMobile=new ArrayList();
                for(int i=0;i<chargeRequestList.size();i++) {
                    ChargeRequest chargeRequests = chargeRequestList.get(i);
                    listOrderNum.add(chargeRequests.getOrderNum());
                    listMobile.add(chargeRequests.getMobile());
                }
                long updateTime = System.currentTimeMillis();//程序开始时长-循环
                    //更新订单
                if (chargeSubmitResponse.getStatus().equals(ChargeSubmitResponse.Status.UNKNOWN)) {
                    //如果提交成功,更新订单状态为充值中
                    chargeOrderMapper.updateStatusByOrderNumList(listOrderNum, ChargeOrder.OrderStatus.CHARGEING_UNKNOWN, chargeRequestList.get(0).getSupplyId());
                    log.info("9、订单进入更新数据阶段----mobile =" +JSON.toJSONString(listMobile) + " orderNum=" +JSON.toJSONString(listOrderNum) + "  提交数据：异常！更新数据：成功");
                }
                if (chargeSubmitResponse.getStatus().equals(ChargeSubmitResponse.Status.SUCCESS)) {
                    //如果提交成功,更新订单状态为充值中
                    chargeOrderMapper.updateStatusByOrderNumList(listOrderNum, ChargeOrder.OrderStatus.CHARGEING, chargeRequestList.get(0).getSupplyId());
                    log.info("9、订单进入更新数据阶段----mobile ="  +JSON.toJSONString(listMobile) + " orderNum=" +JSON.toJSONString(listOrderNum)  + "  提交数据：成功！更新数据：成功");
                }
                if (chargeSubmitResponse.getStatus().equals(ChargeSubmitResponse.Status.FAIL)) {
                    long start1 = System.currentTimeMillis();//程序开始时长-循环
                    for(int i=0;i<chargeRequestList.size();i++) {
                    ChargeRequest chargeRequests = chargeRequestList.get(i);
                    log.info("9、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  提交数据：失败！原因：" + chargeSubmitResponse.getStatusMsg());
                    //退款
                    ChargeOrder chargeOrder = chargeOrderMapper.selectOrderByOrderNum(CommonUtil.getOrderTableName(chargeRequests.getOrderNum()), chargeRequests.getOrderNum());
                    log.info("10、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  提交数据：失败！即将退款---");

                    int refundStatus = ChargeOrder.RefundState.Refund_SUCCESS;//退款成功
                    try {
                        balanceService.refund(chargeOrder.getUserId(), chargeOrder.getAmount(), chargeOrder.getBusinessType(), chargeRequests.getOrderNum());
                        log.info("11、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  提交数据：失败！退款：成功！");
                    } catch (Exception e) {
                        log.info("11、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  提交数据：失败！退款：失败！原因：" + e.getMessage());
                        refundStatus = ChargeOrder.RefundState.Refund_FAIL;//退款失败
                        log.error("11、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  提交数据：失败！退款：失败！原因：" + e.getMessage(), e);
                    }
                    //更新订单状态失败
                    ChargeOrder order = new ChargeOrder();
                    order.setOrderNum(chargeRequests.getOrderNum());
                    order.setStatus(ChargeOrder.OrderStatus.CHARGE_FAIL);
                    order.setSupplyId(chargeRequests.getSupplyId());
                    order.setRefundStatus(refundStatus);
                    order.setTable(CommonUtil.getOrderTableName(chargeRequests.getOrderNum()));//设置查询的表
                    order.setOrderReturnTime(new Date());
                    chargeOrderMapper.updateReturnStatusByOrderNum(order);
                    log.info("12、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  更新订单数据：成功！即将推送状态报告---");

                    //连接消息队列服务
                    MNSClient client = MessageClient.getClient();
                    CloudQueue queue = client.getQueueRef(SysConstants.Queue.PUSH_QUEUE);

                    //推送回调状态
                    User user = userService.findUserByUserId(chargeOrder.getUserId());
                    String token = user.getToken();
                    CallbackReport callbackReport = new CallbackReport();
                    callbackReport.setStatus(new Integer(ChargeOrder.OrderStatus.CHARGE_FAIL).toString());
                    callbackReport.setMobile(chargeRequests.getMobile());
                    callbackReport.setCustomId(chargeOrder.getCustomid());
                    callbackReport.setToken(token);
                    callbackReport.setOrderNum(chargeOrder.getOrderNum());
                    callbackReport.setCallbackUrl(chargeOrder.getCallbackUrl());
                    callbackReport.setPushSum(3);

                    //从redis中获取商家执行时长记录，将执行慢的放入队列中执行
                    String timelenghts = redisTemplate.get(String.valueOf(chargeOrder.getUserId()) + "_time_submit");
                    log.info("商家" + chargeOrder.getUserId() + "推送時長---" + timelenghts);

                    if (timelenghts != null) {
                        //如果超过限定时长，则将该商家的回调放到队列中执行
                        Long time = Long.valueOf(timelenghts);
                        if (time > SysConstants.TIMELONGHT) {
                            //将要推送的内容放到队列中
                            Message message = new Message();
                            message.setMessageBody(JSON.toJSONString(callbackReport));
                            Message putMsg = queue.putMessage(message);
                            log.info("submit-商家" + chargeOrder.getUserId() + "订单号:" + chargeRequests.getOrderNum() + "进队列Push回调---");
                        } else {
                            pushCallbackService.pushCallback(callbackReport);
                        }
                    } else {
                        long startPushTime = System.currentTimeMillis();//开始时间
                        pushCallbackService.pushCallback(callbackReport);
                        long endPushTime = System.currentTimeMillis();//结束时间
                        long timelenght = endPushTime - startPushTime;//执行时长
                        redisTemplate.setExpire(String.valueOf(chargeOrder.getUserId()) + "_time_submit", String.valueOf(timelenght), 3600 * 24);
                        log.info("商家" + chargeOrder.getUserId() + "订单编号" + chargeRequests.getOrderNum() + ",手机号:" + chargeRequests.getMobile() + "推送状态执行时长:" + timelenght);
                    }
                    log.info("13、订单进入更新数据阶段----mobile =" + chargeRequests.getMobile() + " orderNum=" + chargeRequests.getOrderNum() + "  更新订单数据：成功！状态报告推送完毕！");
                 }
                    log.info("处理失败订单消耗时长:"+(System.currentTimeMillis()-start1));
                }
                log.info("湖北流量订单结果变更执行时长:"+(System.currentTimeMillis()-updateTime));
                log.info("湖北流量订单发送执行时长:"+(System.currentTimeMillis()-startReportHandletime));
            } catch (Exception e) {
            log.error("处理消息异常"+e.getMessage(),e);
            //消费失败
        }
        return "SUCCESS";
    }



    /**
     * 更新订单明细
     */
    public void addOrderDetail(int businessType,List<ChargeRequest> chargeRequestList,ChargeSubmitResponse flowSubmitResponse) {
    //更新定单明细
    for(int i=0;i<chargeRequestList.size();i++) {
        ChargeRequest chargeRequest=chargeRequestList.get(i);
        ChargeOrderDetail orderDetail = new ChargeOrderDetail();
        orderDetail.setBusinessType(businessType);
        orderDetail.setChannelNum(chargeRequest.getChannelNum());//渠道流水
        orderDetail.setSupplyChannelNum(flowSubmitResponse.getSupplyChannelNum());//通道流水
        orderDetail.setOrderNum(chargeRequest.getOrderNum());
        orderDetail.setMobile(chargeRequest.getMobile());
        orderDetail.setSupplyId(chargeRequest.getSupplyId());
        orderDetail.setStatus(new Integer(flowSubmitResponse.getStatus()));
        orderDetail.setSubmitRspcode(flowSubmitResponse.getStatusCode());
        orderDetail.setSubmitTime(new Date());
        orderDetail.setReturnRspcode(flowSubmitResponse.getStatusMsg());
        if (flowSubmitResponse.getSupplyOrderNum() != null) {
            redisTemplate.set(flowSubmitResponse.getSupplyChannelNum(), flowSubmitResponse.getSupplyOrderNum());
        }
        if (ChargeSubmitResponse.Status.FAIL.equals(flowSubmitResponse.getStatus())) {
            orderDetail.setReturnTime(new Date());
        }
        chargeOrderDetailMapper.insertSelective(orderDetail);
    }
}
}
