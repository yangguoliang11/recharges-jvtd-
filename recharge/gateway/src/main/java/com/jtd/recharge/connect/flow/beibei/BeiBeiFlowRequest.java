package com.jtd.recharge.connect.flow.beibei;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jtd.recharge.base.util.GroovyConfigUtil;
import com.jtd.recharge.base.util.HttpTookit;
import com.jtd.recharge.bean.ChargeRequest;
import com.jtd.recharge.bean.ChargeSubmitResponse;
import com.jtd.recharge.connect.base.ConnectReqest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @autor jipengkun
 * 流量贝贝 流量 充值
 */
@Service
public class BeiBeiFlowRequest implements ConnectReqest{

    private Log log = LogFactory.getLog(this.getClass());
    private static Properties properties = null;

    private static Map config = new HashMap();

    static {
        config = GroovyConfigUtil.init("config/supply.groovy");
    }

    @Override
    public ChargeSubmitResponse chargeRequest(ChargeRequest chargeRequest) {
        long start =System.currentTimeMillis();
        String supplyName = chargeRequest.getSupplyName();
        Map supplyMap = (Map) config.get(supplyName);
        log.info("8、发送流程：发送供应商---贝贝流量----mobile ="+chargeRequest.getMobile()+" orderNum=" + chargeRequest.getChannelNum());

        String flowType ;
        String host = (String) supplyMap.get("host");
        String orderNo = chargeRequest.getChannelNum();
        String mobile = chargeRequest.getMobile();
        String userName = (String) supplyMap.get("userName");
        String userPwd = DigestUtils.md5Hex((String) supplyMap.get("userPwd"));
        String proKey = chargeRequest.getPositionCode();//充值的流量套餐编码
        String bcallbackUrl = (String) supplyMap.get("callback_url");//回调url

        String interfaceSign = (String) supplyMap.get("interfaceSign");
        String sign = DigestUtils.md5Hex("userName="+userName+"&userPwd="+userPwd+interfaceSign+"&mobile="+mobile+"&proKey="+proKey+"&orderNo="+orderNo+"&bcallbackUrl="+bcallbackUrl);

        String f = "recharge";
//        String flowType = "AF";//AF全网 SF省网全网 SN省网省内
        String businessType = "2";
        if("CUCC_1024M".equals(proKey)){
            flowType="RF";
        }else if("CUCC_30M".equals(proKey)){
            flowType="RF";
        }else if("CUCC_300M".equals(proKey)){
            flowType="RF";
        }else{
            flowType = (String) supplyMap.get("flowType");
        }

        Map param = new HashMap();
        param.put("orderNo",orderNo);
        param.put("mobile",mobile);
        param.put("userName",userName);
        param.put("userPwd",userPwd);
        param.put("proKey",proKey);
        param.put("bcallbackUrl",bcallbackUrl);
        param.put("sign",sign);
        param.put("f",f);
        param.put("flowType",flowType);
        param.put("businessType",businessType);

        log.info("8、发送流程：发送供应商---贝贝流量----mobile ="+chargeRequest.getMobile()+" orderNum=" + chargeRequest.getChannelNum()+"封装应用参数json报文请求参数:"+ JSON.toJSONString(param));
        ChargeSubmitResponse response = new ChargeSubmitResponse();
        String resultContent = "";
        try {
            resultContent = HttpTookit.doPost(host,param);
        } catch (Exception e) {
            log.error("8、发送异常：发送供应商---贝贝流量----mobile="+chargeRequest.getMobile()+" orderNum=" + chargeRequest.getChannelNum()+" 提交到贝贝流量！原因："+ e.getLocalizedMessage());
            response.setStatus(ChargeSubmitResponse.Status.SUCCESS);
            response.setStatusMsg(e.getLocalizedMessage()+"请咨询供应商！");
            return response;
        }
        log.info("8、发送流程：发送供应商---贝贝流量----mobile ="+chargeRequest.getMobile()+" orderNum=" + chargeRequest.getChannelNum()+"返回数据：" + resultContent+"***************发送请求耗时："+
                (System.currentTimeMillis()-start));


        JSONObject object=JSON.parseObject(resultContent);
        String respCode=object.getString("code");
        if(respCode.equals("100")){
            response.setStatus(ChargeSubmitResponse.Status.SUCCESS);
            response.setStatusCode(respCode);
            log.info("8、发送流程：发送供应商---贝贝流量----mobile ="+chargeRequest.getMobile()+" orderNum=" + chargeRequest.getChannelNum()+" 提交到贝贝流量成功！");

        }else{
            response.setStatus(ChargeSubmitResponse.Status.FAIL);
            response.setStatusCode(respCode);
            response.setStatusMsg(respCode+"请咨询供应商！");
            log.info("8、发送流程：发送供应商---贝贝流量----mobile ="+chargeRequest.getMobile()+" orderNum=" + chargeRequest.getChannelNum()+" 提交到贝贝流量失败！原因："+respCode+"请咨询供应商！");
        }

        return response;
    }
}
