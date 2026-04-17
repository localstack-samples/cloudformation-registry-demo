package cloud.localstack.testing.resource;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicRequest;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult;
import com.amazonaws.services.sns.model.Subscription;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import software.amazon.cloudformation.proxy.*;

public class ReadHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        AmazonSQS sqsClient = AmazonSQSClientBuilder.defaultClient();
        AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();

        try {
            // Get the queue ARN from the queue URL
            GetQueueAttributesResult attrs = proxy.<GetQueueAttributesRequest, GetQueueAttributesResult>injectCredentialsAndInvoke(
                    new GetQueueAttributesRequest()
                            .withQueueUrl(model.getQueueUrl())
                            .withAttributeNames("QueueArn"),
                    sqsClient::getQueueAttributes);
            String queueArn = attrs.getAttributes().get("QueueArn");

            // Find the subscription for this queue on the topic
            String nextToken = null;
            String subscriptionArn = null;
            do {
                ListSubscriptionsByTopicResult result = proxy.<ListSubscriptionsByTopicRequest, ListSubscriptionsByTopicResult>injectCredentialsAndInvoke(
                        new ListSubscriptionsByTopicRequest()
                                .withTopicArn(model.getTopicArn())
                                .withNextToken(nextToken),
                        snsClient::listSubscriptionsByTopic);
                for (Subscription sub : result.getSubscriptions()) {
                    logger.log("is this the right subscription: '%s'?".formatted(sub));
                    if ("sqs".equals(sub.getProtocol()) && queueArn.equals(sub.getEndpoint())) {
                        subscriptionArn = sub.getSubscriptionArn();
                        break;
                    }
                }
                nextToken = result.getNextToken();
            } while (subscriptionArn == null && nextToken != null);

            model.setSubscriptionArn(subscriptionArn);

            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
        } catch (Exception e) {
            logger.log("Read failed: " + e.getMessage());
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .status(OperationStatus.FAILED)
                    .errorCode(HandlerErrorCode.GeneralServiceException)
                    .message(e.getMessage())
                    .build();
        }
    }
}
