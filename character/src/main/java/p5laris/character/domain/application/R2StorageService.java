package p5laris.character.domain.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class R2StorageService {

    private final String bucketName;
    private final String publicDomain;
    private final S3Presigner presigner;

    public R2StorageService(
            @Value("${cloud.r2.credentials.access-key:dummy}") String accessKey,
            @Value("${cloud.r2.credentials.secret-key:dummy}") String secretKey,
            @Value("${cloud.r2.endpoint:https://dummy.r2.cloudflarestorage.com}") String endpoint,
            @Value("${cloud.r2.bucket-name:polaris-bucket}") String bucketName,
            @Value("${cloud.r2.public-domain:https://cdn.polaris.app}") String publicDomain) {
        
        this.bucketName = bucketName;
        // Ensure public domain doesn't end with slash
        this.publicDomain = publicDomain.endsWith("/") ? publicDomain.substring(0, publicDomain.length() - 1) : publicDomain;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto")) // R2 requires "auto" or any valid region like "us-east-1"
                .build();
    }

    /**
     * Generates a presigned URL for uploading a share card image.
     * @param extension The file extension (e.g., "png")
     * @return R2PresignedResult containing the upload URL and the final public URL
     */
    public R2PresignedResult generatePresignedUrlForShareCard(String extension) {
        String fileName = "share-cards/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType("image/" + (extension.equals("jpg") ? "jpeg" : extension))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // 10 minutes expiry
                .putObjectRequest(objectRequest)
                .build();

        String presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
        String imageUrl = publicDomain + "/" + fileName;

        return new R2PresignedResult(presignedUrl, imageUrl);
    }

    public record R2PresignedResult(String presignedUrl, String imageUrl) {}
}
