package bit.bitgroundspring.storage;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final StorageProperties props;

    /**
     * 이미지를 업로드하고 공개 URL을 반환한다.
     * 파일명은 원본 이름을 쓰지 않고 UUID로 만들어 경로 조작과 덮어쓰기를 막는다.
     */
    @Loggable("이미지 업로드")
    public String uploadImage(String directory, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "이미지는 5MB 이하만 업로드할 수 있습니다.");
        }
        // 형식은 클라이언트가 보낸 Content-Type이 아니라 파일 앞부분(매직 바이트)으로 정한다.
        // (이름·Content-Type만 이미지로 바꾼 HTML·스크립트 파일을 이미지로 저장하지 않게)
        if (!IMAGE_EXTENSIONS.containsKey(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        String contentType = detectImageType(file);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        String extension = IMAGE_EXTENSIONS.get(contentType);

        String key = directory + "/" + LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + "." + extension;
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException e) { // 스토리지에 연결할 수 없을 때도 500 대신 '업로드 실패'로 알린다
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
        log.info("[storage] uploaded {}", key);
        return props.resolvedPublicUrl() + "/" + key;
    }

    /** 파일 앞부분으로 jpg·png·gif·webp를 구분한다. 어느 것도 아니면 null */
    static String detectImageType(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 8 && startsWith(head, PNG_SIGNATURE, 0)) {
            return "image/png";
        }
        if (read >= 6 && (startsWith(head, "GIF87a".getBytes(StandardCharsets.US_ASCII), 0)
                || startsWith(head, "GIF89a".getBytes(StandardCharsets.US_ASCII), 0))) {
            return "image/gif";
        }
        if (read >= 12 && startsWith(head, "RIFF".getBytes(StandardCharsets.US_ASCII), 0)
                && startsWith(head, "WEBP".getBytes(StandardCharsets.US_ASCII), 8)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 이 스토리지에 올린 파일이면 삭제한다. (소셜 프로필 이미지 같은 외부 URL은 무시)
     */
    public void deleteByUrl(String url) {
        String base = props.resolvedPublicUrl() + "/";
        if (url == null || !url.startsWith(base)) {
            return;
        }
        String key = url.substring(base.length());
        // 이전 사진 정리는 부가 작업이라 스토리지가 잠시 안 돼도 프로필 저장은 그대로 진행한다 (남은 파일은 나중에 정리)
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
            log.info("[storage] deleted {}", key);
        } catch (SdkException e) {
            log.warn("[storage] 삭제 실패, 건너뜀 {}: {}", key, e.getMessage());
        }
    }
}
