# LocalStack::Testing::Resource

Lambda compatible queue for subscribing to messages

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "LocalStack::Testing::Resource",
    "Properties" : {
        "<a href="#topicarn" title="TopicArn">TopicArn</a>" : <i>String</i>,
    }
}
</pre>

### YAML

<pre>
Type: LocalStack::Testing::Resource
Properties:
    <a href="#topicarn" title="TopicArn">TopicArn</a>: <i>String</i>
</pre>

## Properties

#### TopicArn

ARN of the topic

_Required_: Yes

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

## Return Values

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### QueueUrl

URL of the queue

#### SubscriptionArn

Arn of the created subscription

