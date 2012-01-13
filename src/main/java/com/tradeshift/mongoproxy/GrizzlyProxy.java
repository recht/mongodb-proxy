package com.tradeshift.mongoproxy;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

public class GrizzlyProxy {

	public static void main(String[] args) {
		TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
		FilterChainBuilder fb = FilterChainBuilder.stateless();
		fb.add(new TransportFilter());
		fb.add(new TunnelFilter(TCPNIOConnectorHandler.builder(transport).build(), "localhost", 27017));
		
		transport.setProcessor(fb.build());
		try {
			transport.bind(29017);
			transport.start();
			
			System.out.println("GrizzlyProxy.main()");
			System.in.read();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static class ProxyFilter extends BaseFilter {
		@Override
		public NextAction handleRead(FilterChainContext ctx) throws IOException {
			Object msg = ctx.getMessage();
			
			return super.handleRead(ctx);
		}
	}
}
