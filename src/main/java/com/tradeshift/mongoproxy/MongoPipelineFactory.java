package com.tradeshift.mongoproxy;


import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

public class MongoPipelineFactory implements ChannelPipelineFactory {

      private final ClientSocketChannelFactory cf;
      private final String remoteHost;
      private final int remotePort;
  
      public MongoPipelineFactory(ClientSocketChannelFactory cf, String remoteHost, int remotePort) {
          this.cf = cf;
          this.remoteHost = remoteHost;
          this.remotePort = remotePort;
      }
  
      public ChannelPipeline getPipeline() throws Exception {
          ChannelPipeline p = Channels.pipeline(); // Note the static import.
          p.addLast("handler", new InboundMongoHandler(cf, remoteHost, remotePort));
          return p;
      }
}
