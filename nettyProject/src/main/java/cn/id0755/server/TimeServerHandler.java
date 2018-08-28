package cn.id0755.server;

import cn.id0755.cn.id0755.utils.Log;
import cn.id0755.nettystudy.ch8.SubscribeReqProto;
import cn.id0755.nettystudy.ch8.SubscribeRespProto;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TimeServerHandler extends ChannelInboundHandlerAdapter {
    private final static String TAG = "TimeServerHandler";
    private volatile int counter;
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SubscribeReqProto.SubscribeReq req = (SubscribeReqProto.SubscribeReq)msg;
        Log.d(TAG, "Time server receive order:[" + req.toString() +
                "]");
        ctx.writeAndFlush(resp(req.getSubReqID()));
    }

    private SubscribeRespProto.SubscribeResp resp(int subReqId){
        SubscribeRespProto.SubscribeResp.Builder builder =
                SubscribeRespProto.SubscribeResp.newBuilder();

        builder.setSubReqID(subReqId)
                .setRespCode(200)
                .setDesc("Netty Ok");
        return builder.build();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        super.channelReadComplete(ctx);
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
