package cn.id0755.server;

import cn.id0755.cn.id0755.utils.Log;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable
public class EchoServerHandler2 extends ChannelInboundHandlerAdapter{
    private final static String TAG = "EchoServerHandler2";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        super.channelRead(ctx, msg);
        DatagramPacket packet = (DatagramPacket)msg;
        ByteBuf in = packet.copy().content();
        Log.d(TAG, "Server received: " + in.toString(CharsetUtil.UTF_8));
        ctx.write(new DatagramPacket(Unpooled.copiedBuffer(in),packet.sender()));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        super.channelReadComplete(ctx);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
//                .addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }
}
