package cn.id0755.im;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import cn.id0755.nettystudy.ch8.SubscribeReqProto;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TimeClientHandler extends ChannelInboundHandlerAdapter {
    private final static String TAG = "TimeClientHandler";
    private byte[] req;
    private volatile int counter;

    public TimeClientHandler() {
        req = ("QUERY TIME ORDER" + "$_").getBytes();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        super.channelActive(ctx);
        ByteBuf message = null;

        for (int i = 0; i < 100; i++) {
            ctx.write(subscribeReq(i));
        }
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Log.d(TAG, "Receive server response : [" + msg + "]");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Log.e(TAG, "exceptionCaught:" + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        super.channelReadComplete(ctx);
        ctx.flush();
    }

    private SubscribeReqProto.SubscribeReq subscribeReq(int i){
        SubscribeReqProto.SubscribeReq.Builder builder =
                SubscribeReqProto.SubscribeReq.newBuilder();

        builder.setProductName("Netty in action");
        builder.setSubReqID(i);
        builder.setUserName("atto");

        List<String> adds = new ArrayList<>();
        adds.add("cd");
        adds.add("sh");
        adds.add("nb");
        builder.addAllAddress(adds);

        return builder.build();
    }
}
