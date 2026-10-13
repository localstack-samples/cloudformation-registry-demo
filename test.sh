#!/bin/bash
# Check the LocalStack::Testing::Resource extension end to end:
# 1. the type is registered and is the default version
# 2. a stack using it creates a queue subscribed to the topic
# 3. a message published to the topic reaches the queue
# 4. deleting the stack removes the queue and the subscription
set -euo pipefail

TYPE_NAME=LocalStack::Testing::Resource
STACK_NAME=${STACK_NAME:-registry-demo-test}

fail() {
  echo "Test failed: $*"
  exit 1
}

show_events() {
  lstk aws cloudformation describe-stack-events --stack-name "$STACK_NAME" \
    --query 'StackEvents[].[LogicalResourceId, ResourceStatus, ResourceStatusReason]' --output text || true
}

registered=$(lstk aws cloudformation describe-type --type RESOURCE --type-name "$TYPE_NAME" \
  --query '[Visibility, IsDefaultVersion]' --output text)
[ "$registered" = "PRIVATE	True" ] || fail "$TYPE_NAME is not registered as the default private version: '$registered'"
echo "PASS: $TYPE_NAME is registered as the default version"

if ! lstk aws cloudformation deploy --stack-name "$STACK_NAME" --template-file testtemplate.yaml; then
  show_events
  fail "deploying testtemplate.yaml failed"
fi

output() {
  lstk aws cloudformation describe-stacks --stack-name "$STACK_NAME" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}
TOPIC_ARN=$(output TopicArn)
QUEUE_URL=$(output QueueUrl)
SUBSCRIPTION_ARN=$(output SubscriptionArn)
echo "Topic: $TOPIC_ARN"
echo "Queue: $QUEUE_URL"
echo "Subscription: $SUBSCRIPTION_ARN"
[ -n "$QUEUE_URL" ] && [ "$QUEUE_URL" != "None" ] || fail "the stack has no QueueUrl output"
[ -n "$SUBSCRIPTION_ARN" ] && [ "$SUBSCRIPTION_ARN" != "None" ] || fail "the stack has no SubscriptionArn output"

QUEUE_ARN=$(lstk aws sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn \
  --query Attributes.QueueArn --output text)
policy=$(lstk aws sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names Policy \
  --query Attributes.Policy --output text)
echo "$policy" | jq -e --arg topic "$TOPIC_ARN" \
  '.Statement[0].Action == "sqs:SendMessage" and .Statement[0].Condition.ArnEquals["aws:SourceArn"] == $topic' > /dev/null \
  || fail "the queue policy does not let the topic send messages: $policy"
echo "PASS: the extension created queue $QUEUE_ARN with a policy for the topic"

subscribed=$(lstk aws sns list-subscriptions-by-topic --topic-arn "$TOPIC_ARN" \
  --query "Subscriptions[?Protocol=='sqs' && Endpoint=='$QUEUE_ARN'].SubscriptionArn" --output text)
[ "$subscribed" = "$SUBSCRIPTION_ARN" ] || fail "the topic subscription for the queue is '$subscribed', expected $SUBSCRIPTION_ARN"
echo "PASS: the queue is subscribed to the topic"

MESSAGE="registry demo $(date +%s)"
lstk aws sns publish --topic-arn "$TOPIC_ARN" --message "$MESSAGE" > /dev/null
received=""
for attempt in 1 2 3 4 5 6; do
  received=$(lstk aws sqs receive-message --queue-url "$QUEUE_URL" --wait-time-seconds 5 \
    --query 'Messages[0].Body' --output text)
  [ -n "$received" ] && [ "$received" != "None" ] && break
done
[ "$(echo "$received" | jq -r .Message 2>/dev/null)" = "$MESSAGE" ] || fail "the queue did not receive the published message, got '$received'"
echo "PASS: a message published to the topic reached the queue"

lstk aws cloudformation delete-stack --stack-name "$STACK_NAME"
if ! lstk aws cloudformation wait stack-delete-complete --stack-name "$STACK_NAME"; then
  show_events
  fail "deleting the stack failed"
fi
if lstk aws sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn > /dev/null 2>&1; then
  fail "queue $QUEUE_URL still exists after the stack was deleted"
fi
remaining=$(lstk aws sns list-subscriptions --query "Subscriptions[?SubscriptionArn=='$SUBSCRIPTION_ARN'] | length(@)" --output text)
[ "$remaining" = "0" ] || fail "subscription $SUBSCRIPTION_ARN still exists after the stack was deleted"
echo "PASS: deleting the stack removed the queue and the subscription"

echo "Test passed: the extension manages the queue and subscription through the stack lifecycle"
