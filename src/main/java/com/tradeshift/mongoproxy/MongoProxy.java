package com.tradeshift.mongoproxy;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class MongoProxy {

	public static void main(String[] args) {
		Executor executor = Executors.newCachedThreadPool();
		ServerBootstrap sb = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));

		// Set up the event pipeline factory.
		ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);

		sb.setPipelineFactory(new MongoPipelineFactory(cf, "localhost", 27017));

		// Start up the server.
		sb.bind(new InetSocketAddress(29017));
		System.out.println("Started");
	}
}
