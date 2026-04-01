package cloud.localstack.testing.resource;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import software.amazon.cloudformation.proxy.*;

import java.util.Collections;

public class CreateHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        AmazonSQS sqsClient = AwsClientFactory.configure(AmazonSQSClientBuilder.standard()).build();
        AmazonSNS snsClient = AwsClientFactory.configure(AmazonSNSClientBuilder.standard()).build();

        String queueName = String.format("%s-%s",
                request.getLogicalResourceIdentifier(),
                request.getClientRequestToken().substring(0, 8));

        try {
            // 1. create queue
            CreateQueueResult createQueueResult = proxy.<CreateQueueRequest, CreateQueueResult>injectCredentialsAndInvoke(
                    new CreateQueueRequest().withQueueName(queueName),
                    sqsClient::createQueue
            );
            String queueUrl = createQueueResult.getQueueUrl();
            model.setQueueUrl(queueUrl);

            // 2. get queue arn
            GetQueueAttributesResult attrs = proxy.<GetQueueAttributesRequest, GetQueueAttributesResult>injectCredentialsAndInvoke(
                    new GetQueueAttributesRequest().withQueueUrl(queueUrl)
                            .withAttributeNames("QueueArn"),
                    sqsClient::getQueueAttributes);
            String queueArn = attrs.getAttributes().get("QueueArn");

            // 3. set queue policy to allow sns
            String policy = buildPolicy(queueArn, model.getTopicArn());
            proxy.<SetQueueAttributesRequest, SetQueueAttributesResult>injectCredentialsAndInvoke(
                    new SetQueueAttributesRequest()
                            .withQueueUrl(queueUrl)
                            .withAttributes(Collections.singletonMap("Policy", policy)),
                    sqsClient::setQueueAttributes);

            // 4. subscribe queue to sns topic
            SubscribeResult subscribeResult = proxy.<SubscribeRequest, SubscribeResult>injectCredentialsAndInvoke(
                    new SubscribeRequest()
                            .withTopicArn(model.getTopicArn())
                            .withProtocol("sqs")
                            .withEndpoint(queueArn),
                    snsClient::subscribe);
            model.setSubscriptionArn(subscribeResult.getSubscriptionArn());

            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
        } catch (Exception e) {
            logger.log("Create failed: " + e.getMessage());
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .status(OperationStatus.FAILED)
                    .errorCode(HandlerErrorCode.GeneralServiceException)
                    .message(e.getMessage())
                    .build();
        }
    }

    private String buildPolicy(String queueArn, String topicArn) {
        return "{"
                + "\"Version\":\"2012-10-17\","
                + "\"Statement\":[{"
                + "\"Effect\":\"Allow\","
                + "\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\","
                + "\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}"
                + "}]"
                + "}";
    }

}
