package p5laris.character.domain.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class S3StorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    private final String bucketName;
    private final String publicDomain;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3StorageService(
            @Value("${cloud.aws.region:ap-northeast-2}") String region,
            @Value("${cloud.aws.s3.bucket-name:polaris-share-cards}") String bucketName,
            @Value("${cloud.aws.s3.public-domain:https://cdn.polaris.app}") String publicDomain) {

        this.bucketName = bucketName;
        this.publicDomain = publicDomain.endsWith("/")
                ? publicDomain.substring(0, publicDomain.length() - 1)
                : publicDomain;

        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        Region awsRegion = Region.of(region);

        this.s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(awsRegion)
                .build();

        this.presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(awsRegion)
                .build();
    }

    public S3UploadResult uploadShareCardImage(byte[] imageBytes, String contentType) {
        String fileName = "share-cards/" + UUID.randomUUID() + ".png";

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        s3Client.putObject(objectRequest, RequestBody.fromBytes(imageBytes));

        return new S3UploadResult(fileName, publicDomain + "/" + fileName);
    }

    public S3PresignedResult generatePresignedUrlForShareCard(String extension) {
        String safeExt = normalizeAndValidateExtension(extension);
        String fileName = "share-cards/" + UUID.randomUUID() + "." + safeExt;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType("image/" + (safeExt.equals("jpg") ? "jpeg" : safeExt))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        String presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
        String imageUrl = publicDomain + "/" + fileName;

        return new S3PresignedResult(presignedUrl, imageUrl);
    }

    private String normalizeAndValidateExtension(String extension) {
        if (extension == null) {
            return "png";
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(normalized)) {
            return "png";
        }
        return normalized;
    }

    public record S3UploadResult(String key, String imageUrl) {}

    public record S3PresignedResult(String presignedUrl, String imageUrl) {}
}
