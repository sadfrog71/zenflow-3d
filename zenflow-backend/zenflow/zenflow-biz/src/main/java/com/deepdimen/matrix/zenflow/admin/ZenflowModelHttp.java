package com.deepdimen.matrix.zenflow.admin;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

/**
 * Shared connection policy for product model calls: public DNS only, HTTPS, no
 * redirects/retries/system proxy.
 */
public final class ZenflowModelHttp {

	private ZenflowModelHttp() {
	}

	public static CloseableHttpClient client(URI endpoint) {
		ZenflowModelService.validateEndpoint(endpoint.toString());
		DnsResolver resolver = new DnsResolver() {
			public InetAddress[] resolve(String host) throws UnknownHostException {
				if (!host.equalsIgnoreCase(endpoint.getHost()))
					throw new UnknownHostException();
				ZenflowModelService.validateEndpoint("https://" + (host.contains(":") ? "[" + host + "]" : host));
				InetAddress[] addresses = InetAddress.getAllByName(host);
				for (InetAddress address : addresses)
					ZenflowModelService.validateEndpoint("https://" + (address.getHostAddress().contains(":")
							? "[" + address.getHostAddress() + "]" : address.getHostAddress()));
				return addresses;
			}

			public String resolveCanonicalHostname(String host) {
				return host;
			}
		};
		var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver).build();
		return HttpClients.custom()
			.setConnectionManager(manager)
			.disableRedirectHandling()
			.disableAutomaticRetries()
			.setDefaultRequestConfig(RequestConfig.custom()
				.setConnectionRequestTimeout(Timeout.ofSeconds(5))
				.setConnectTimeout(Timeout.ofSeconds(10))
				.setResponseTimeout(Timeout.ofSeconds(25))
				.build())
			.build();
	}

}
