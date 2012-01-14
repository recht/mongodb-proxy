/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.tradeshift.mongoproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.List;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpcodeFilter extends BaseFilter {
	private static final Logger log = LoggerFactory.getLogger(OpcodeFilter.class);
	
    private Attribute<Connection<?>> peerConnectionAttribute = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("OpcodeFilter.peerConnection");

    // Transport, which will be used to create peer connection
    private final SocketConnectorHandler transport;

    // Destination address for peer connections
    private final SocketAddress redirectAddress;
    private final BitSet rejectOpcodes = new BitSet();
    private int requestId = 0;

    public OpcodeFilter(SocketConnectorHandler transport, String host, int port, List<Integer> rejectOpcodes) {
        this(transport, new InetSocketAddress(host, port));
        log.info("Rejecting opcodes {}", rejectOpcodes);
        if (rejectOpcodes != null) {
        	for (Integer opCode : rejectOpcodes) {
				this.rejectOpcodes.set(opCode);
			}
        }
    }

    public OpcodeFilter(SocketConnectorHandler transport, SocketAddress redirectAddress) {
        this.transport = transport;
        this.redirectAddress = redirectAddress;
    }
    
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
    	log.debug("New connection: {}", ctx.getConnection());
    	return super.handleConnect(ctx);
    }
    
    /**
     * This method will be called, once {@link Connection} has some available data
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        final Connection<?> peerConnection = peerConnectionAttribute.get(connection);

        // if connection is closed - stop the execution
        if (!connection.isOpen()) {
            return ctx.getStopAction();
        }

        // if peerConnection wasn't created - create it (usually happens on first connection request)
        if (peerConnection == null) {
        	log.debug("Connecting to mongo at {}", redirectAddress);
            // "Peer connect" phase could take some time - so execute it in non-blocking mode

            // Connect peer connection and register completion handler
            transport.connect(redirectAddress, new ConnectCompletionHandler(ctx));

            // return suspend status
            return ctx.getSuspendAction();
        }

        Buffer msg = ctx.getMessage();
        int length = msg.remaining();
        if (length < 16) {
        	return ctx.getStopAction(msg);
        }
        msg.mark();
        msg.order(ByteOrder.LITTLE_ENDIAN);
        int msgLength = msg.getInt();
        int requestId = msg.getInt();
        @SuppressWarnings("unused")
		int responseTo = msg.getInt();
        int opCode = msg.getInt();
        msg.reset();
        
        
        if (length < msgLength) {
        	return ctx.getStopAction(msg);
        }
        final Buffer remainder = length > msgLength ?  msg.split(msgLength) : null;
        
        if (rejectOpcodes.get(opCode)) {
        	log.warn("Opcode {} from {} ({}) will be rejected", new Object[] { opCode, ctx.getAddress(), requestId });

        	Buffer out = connection.getTransport().getMemoryManager().allocate(36);
        	out.order(ByteOrder.LITTLE_ENDIAN);
        	out.putInt(36);
        	out.putInt(this.requestId++);
        	out.putInt(requestId);
        	out.putInt(opCode);
        	out.putInt(0); // flags
        	out.putLong(0); // cursor
        	out.putInt(0); // startingFrom
        	out.putInt(1); // numdocs
        	
        	ctx.write(out, true);
//        	out.dispose();
        	
        	return ctx.getInvokeAction(remainder);
        }
        
        // if peer connection is already created - just forward data to peer
        redirectToPeer(ctx, peerConnection);
        msg.tryDispose();

        return ctx.getInvokeAction(remainder);
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        final Connection<?> peerConnection = peerConnectionAttribute.get(connection);

        // Close peer connection as well, if it wasn't closed before
        if (peerConnection != null && peerConnection.isOpen()) {
            peerConnection.close();
        }
        log.debug("Closing connection {} with peer {}", connection, peerConnection);

        return ctx.getInvokeAction();
    }

    /**
     * Redirect data from {@link Connection} to its peer.
     */
    private static void redirectToPeer(final FilterChainContext context, final Connection<?> peerConnection) throws IOException {
        final Object message = context.getMessage();
        peerConnection.write(message);
    }
    
    private class ConnectCompletionHandler implements CompletionHandler<Connection> {
        private final FilterChainContext context;
        
        private ConnectCompletionHandler(FilterChainContext context) {
            this.context = context;
        }

        @Override
        public void cancelled() {
            close(context.getConnection());
            resumeContext();
        }

        @Override
        public void failed(Throwable throwable) {
            close(context.getConnection());
            resumeContext();
        }

        /**
         * If peer was successfully connected - map both connections to each other.
         */
        @Override
        public void completed(@SuppressWarnings("rawtypes") Connection peerConnection) {
            final Connection<?> connection = context.getConnection();

            // Map connections
            peerConnectionAttribute.set(connection, peerConnection);
            peerConnectionAttribute.set(peerConnection, connection);

            // Resume filter chain execution
            resumeContext();
        }

        @Override
        public void updated(@SuppressWarnings("rawtypes") Connection peerConnection) {
        }

        private void resumeContext() {
            context.resume();
        }

        private void close(@SuppressWarnings("rawtypes") Connection connection) {
            try {
                connection.close();
            } catch (IOException e) {
            }
        }
    }
}
