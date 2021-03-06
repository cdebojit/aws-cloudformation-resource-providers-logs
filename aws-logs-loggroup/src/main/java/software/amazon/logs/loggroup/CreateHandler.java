package software.amazon.logs.loggroup;

import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.resource.IdentifierUtils;
import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;

import java.util.Objects;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final String DEFAULT_LOG_GROUP_NAME_PREFIX = "LogGroup";
    private static final int MAX_LENGTH_LOG_GROUP_NAME = 512;

    private AmazonWebServicesClientProxy proxy;
    private ResourceHandlerRequest<ResourceModel> request;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        this.proxy = proxy;
        this.request = request;
        this.logger = logger;

        prepareResourceModel();

        final ResourceModel model = request.getDesiredResourceState();

        try {
            proxy.injectCredentialsAndInvokeV2(Translator.translateToCreateRequest(model),
                ClientBuilder.getClient()::createLogGroup);
        } catch (final ResourceAlreadyExistsException e) {
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME,
                Objects.toString(model.getPrimaryIdentifier()));
        }

        final String createMessage = String.format("%s [%s] successfully created.",
                ResourceModel.TYPE_NAME, model.getLogGroupName());
        logger.log(createMessage);

        if (model.getRetentionInDays() != null) {
            updateRetentionInDays();
        }

        return ProgressEvent.defaultSuccessHandler(model);
    }

    /**
     * Since the resource model itself can be null, as well as any of its attributes,
     * we need to prepare a model to safely operate on. This includes:
     *
     * 1. Setting an empty logical resource ID if it is null. Each real world request should
     *    have a logical ID, but we don't want the log name generation to depend on it.
     * 2. Generating a log name if one is not given. This is a createOnly property,
     *    but we generate one if one is not provided.
     */
    private void prepareResourceModel() {
        if (request.getDesiredResourceState() == null) {
            request.setDesiredResourceState(new ResourceModel());
        }

        final ResourceModel model = request.getDesiredResourceState();
        final String identifierPrefix = request.getLogicalResourceIdentifier() == null ?
            DEFAULT_LOG_GROUP_NAME_PREFIX :
            request.getLogicalResourceIdentifier();

        if (StringUtils.isNullOrEmpty(model.getLogGroupName())) {
            model.setLogGroupName(
                IdentifierUtils.generateResourceIdentifier(
                    identifierPrefix,
                    request.getClientRequestToken(),
                    MAX_LENGTH_LOG_GROUP_NAME
                )
            );
        }
    }

    private void updateRetentionInDays() {
        final ResourceModel model = request.getDesiredResourceState();
        proxy.injectCredentialsAndInvokeV2(Translator.translateToPutRetentionPolicyRequest(model),
            ClientBuilder.getClient()::putRetentionPolicy);

        final String retentionPolicyMessage =
            String.format("%s [%s] successfully applied retention in days: [%d].",
                ResourceModel.TYPE_NAME, model.getLogGroupName(), model.getRetentionInDays());
        logger.log(retentionPolicyMessage);
    }
}
