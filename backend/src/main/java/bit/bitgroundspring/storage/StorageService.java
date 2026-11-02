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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
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
        String extension = IMAGE_EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String key = directory + "/" + LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + "." + extension;
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, e);
        }
        log.info("[storage] uploaded {}", key);
        return props.resolvedPublicUrl() + "/" + key;
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
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
        log.info("[storage] deleted {}", key);
    }
}
