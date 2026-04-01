package cloud.localstack.testing.resource;

import com.amazonaws.client.builder.AwsClientBuilder;

public class AwsClientFactory {
    private static final String ENDPOINT = System.getenv("AWS_ENDPOINT_URL");
    private static final String REGION = System.getProperty("aws.region", "us-east-1");

    public static <T extends AwsClientBuilder<?, ?>> T configure(T builder) {
        if (ENDPOINT != null && !ENDPOINT.isEmpty()) {
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(ENDPOINT, REGION));
        }
        return builder;
    }
}
