package cloud.localstack.testing.resource;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.UnsubscribeRequest;
import com.amazonaws.services.sns.model.UnsubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.DeleteQueueResult;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel prevModel = request.getPreviousResourceState();
        final ResourceModel model = request.getDesiredResourceState();

        logger.log("got previous resource model: %s".formatted(prevModel));
        logger.log("got resource model: %s".formatted(model));

        AmazonSQS sqsClient = AwsClientFactory.configure(AmazonSQSClientBuilder.standard()).build();
        AmazonSNS snsClient = AwsClientFactory.configure(AmazonSNSClientBuilder.standard()).build();

        try {
            var subscriptionArn = model.getSubscriptionArn();
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
}
