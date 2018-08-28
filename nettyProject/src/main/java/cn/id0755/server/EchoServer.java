package cn.id0755.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class EchoServer {
    public static void main(String[] args) throws InterruptedException
    {
        Bootstrap b = new Bootstrap();
        EventLoopGroup group = new NioEventLoopGroup();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .localAddress(9999)
                .handler(new EchoServerHandler2());

        // 服务端监听在9999端口
        ChannelFuture channelFuture = b.bind().sync();
        channelFuture.channel().closeFuture().sync();
    }
}
