package bittech.dae.controller.ln.lnd;

import java.io.File;
import java.util.concurrent.TimeUnit;

import bittech.lib.utils.exceptions.StoredException;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;

public class LndRpc implements AutoCloseable {
	
	private final ManagedChannel channel;
	
	public LndRpc(String host, int port, String cert) {
		try {
			SslContextBuilder builder = GrpcSslContexts.forClient();
			builder.trustManager(new File(cert));

			SslContext sslContext = builder.build();

			channel = NettyChannelBuilder.forAddress(host, port).maxInboundMessageSize(Integer.MAX_VALUE).negotiationType(NegotiationType.TLS)
					.sslContext(sslContext).build();
		} catch (Exception ex) {
			throw new StoredException("Cannot initialize gRPC SSL connection to lnd", ex);
		}
	}
	
	@Override
	public void close() {
		try {
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (Exception ex) {
			throw new StoredException("Closing LndCommandExxecutor failed", ex);
		}
	}
	
	public ManagedChannel getChannel() {
		return channel;
	}

}
