package cloud.localstack.testing.resource;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.DeleteQueueResult;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {

    private AmazonWebServicesClientProxy proxy;
    private AmazonSQS sqsClient;
    private AmazonSNS snsClient;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        this.proxy = proxy;
        this.logger = logger;

        logger.log("got resource model: %s".formatted(model));
        logger.log("thanks claude 2");

        sqsClient = AwsClientFactory.configure(AmazonSQSClientBuilder.standard()).build();
        snsClient = AwsClientFactory.configure(AmazonSNSClientBuilder.standard()).build();

        try {
            var subscriptionArn = getSubscriptionArn(model);
            logger.log("unsubscribing from subscription arn: '%s'".formatted(subscriptionArn));
            proxy.<UnsubscribeRequest, UnsubscribeResult>injectCredentialsAndInvoke(
                    new UnsubscribeRequest()
                            .withSubscriptionArn(subscriptionArn),
                    snsClient::unsubscribe);

            var queueUrl = model.getQueueUrl();
            logger.log("deleting queue: '%s'".formatted(queueUrl));
            proxy.<DeleteQueueRequest, DeleteQueueResult>injectCredentialsAndInvoke(
                    new DeleteQueueRequest()
                            .withQueueUrl(queueUrl),
                    sqsClient::deleteQueue);

            logger.log("success");
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.SUCCESS)
                    .build();
        } catch (Exception e) {
            logger.log("Delete failed: " + e.getMessage());
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .status(OperationStatus.FAILED)
                    .errorCode(HandlerErrorCode.GeneralServiceException)
                    .message(e.getMessage())
                    .build();

        }
    }

    private String getSubscriptionArn(ResourceModel model) {
        // Get the queue ARN from the queue URL
        GetQueueAttributesResult attrs = proxy.<GetQueueAttributesRequest, GetQueueAttributesResult>injectCredentialsAndInvoke(
                new GetQueueAttributesRequest()
                        .withQueueUrl(model.getQueueUrl())
                        .withAttributeNames("QueueArn"),
                sqsClient::getQueueAttributes);
        String queueArn = attrs.getAttributes().get("QueueArn");

        logger.log("got queue arn: %s".formatted(queueArn));

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

        return subscriptionArn;
    }
}
