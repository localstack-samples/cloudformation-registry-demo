VENV ?= .venv
PROFILE ?= localstack
STACK_NAME ?= registry-demo

# cfn generate runs during the Maven build, so the CloudFormation CLI must be on PATH
export PATH := $(CURDIR)/$(VENV)/bin:$(PATH)

.PHONY: usage install build submit deploy test destroy

usage:       ## Show this help
	@grep -h '##' $(MAKEFILE_LIST) | grep -v grep | sed -e 's/##//'

install:     ## Install the CloudFormation CLI into a virtualenv
	python3 -m venv $(VENV)
	$(VENV)/bin/pip install -r requirements.txt

build:       ## Build the extension jar
	mvn -B package

submit:      ## Register the extension and make it the default version
	cfn submit --set-default --profile $(PROFILE)

deploy:      ## Deploy testtemplate.yaml, which uses the extension
	lstk aws cloudformation deploy --stack-name $(STACK_NAME) --template-file testtemplate.yaml

test:        ## Deploy a stack with the extension, check it, then delete it
	STACK_NAME=$(STACK_NAME)-test ./test.sh

destroy:     ## Delete the stack created by make deploy
	lstk aws cloudformation delete-stack --stack-name $(STACK_NAME)
	lstk aws cloudformation wait stack-delete-complete --stack-name $(STACK_NAME)
