package uk.nhs.nhsx.diagnosiskeydist;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.nhs.nhsx.core.Environment;
import uk.nhs.nhsx.core.SystemClock;
import uk.nhs.nhsx.core.aws.cloudfront.AwsCloudFrontClient;
import uk.nhs.nhsx.core.aws.s3.AwsS3Client;
import uk.nhs.nhsx.core.aws.ssm.AwsSsmParameters;
import uk.nhs.nhsx.core.aws.ssm.Parameters;
import uk.nhs.nhsx.diagnosiskeydist.keydistribution.UploadToS3KeyDistributor;
import uk.nhs.nhsx.diagnosiskeydist.s3.SubmissionFromS3Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static uk.nhs.nhsx.core.StandardSigning.datedSigner;
import static uk.nhs.nhsx.core.StandardSigning.signContentWithKeyFromParameter;

/**
 * Scheduling strategy:
 * - cron(47 1,3,5,7,9,11,13,15,17,19,21,23 * * ? *)
 * <p>
 * Dependencies
 * - Submissions bucket: submissions must be deleted >14 days after upload (S3 Lifecycle Policy)
 * <p>
 * Error handling strategy:
 * - Fail fast (e.g. error in one thread -> stop all threads immediately)
 * - Log errors
 * - Error alerting (ERROR entries in log group -> error metric -> error alert)
 */
public class Handler implements RequestHandler<ScheduledEvent, String> {

    private static final Logger logger = LogManager.getLogger(Handler.class);
    private static final String MOBILE_APP_BUNDLE_ID = "MOBILE_APP_BUNDLE_ID";

    private final Environment environment;
    private final BatchProcessingConfig batchProcessingConfig;

    public Handler() {
        this(Environment.fromSystem());
    }

    public Handler(Environment environment) {
        this.environment = environment;
        batchProcessingConfig = BatchProcessingConfig.fromEnvironment(this.environment);
    }

    public String handleRequest(ScheduledEvent input, Context context) {
        Date systemTime = new Date();

        Supplier<Instant> clock = SystemClock.CLOCK;

        try {
            logger.info("Begin: Key distribution batch");

            AwsS3Client awsS3Client = new AwsS3Client();
            Parameters parameters = new AwsSsmParameters();
            SubmissionFromS3Repository submissionRepository;
            String[] allowedPrefixes = environment.access.required("DIAGNOSIS_KEY_SUBMISSION_PREFIXES").split(",");
            if (allowedPrefixes.length != 0) {

                Predicate<String> matchesPrefix = objectKey -> (Arrays.stream(allowedPrefixes).anyMatch(objectKey::startsWith));
                submissionRepository = new SubmissionFromS3Repository(awsS3Client, matchesPrefix);
            } else submissionRepository = new SubmissionFromS3Repository(awsS3Client);

            new DistributionService(
                submissionRepository,
                new ExposureProtobuf(environment.access.required(MOBILE_APP_BUNDLE_ID)),
                new UploadToS3KeyDistributor(awsS3Client, datedSigner(clock, parameters, batchProcessingConfig.ssmMetaDataSigningKeyParameterName)),
                signContentWithKeyFromParameter(parameters, batchProcessingConfig.ssmAGSigningKeyParameterName),
                new AwsCloudFrontClient(),
                awsS3Client,
                batchProcessingConfig
            ).distributeKeys(systemTime);

            logger.info("Success: Key distribution batch");

            return "success";
        } catch (Exception e) {
            logger.error("Failed: Key distribution batch", e);

            throw new RuntimeException(e);
        }
    }
}
