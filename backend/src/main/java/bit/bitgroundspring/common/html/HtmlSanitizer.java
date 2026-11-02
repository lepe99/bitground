package bit.bitgroundspring.common.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 게시글·공지 본문 HTML 정제 (저장 시점)
 *
 * 프론트도 렌더링할 때 sanitize-html로 정제하지만, DB에 원본이 남으면 다른 클라이언트나
 * 관리 도구에서 그대로 노출될 수 있으므로 서버에서도 허용 목록으로 걸러 저장한다.
 * 허용 목록은 Quill 에디터가 만드는 서식과 같다 (frontend/src/utils/sanitize.js).
 */
@Component
public class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "h1", "h2", "h3", "strong", "b", "em", "i", "u", "s",
                    "ol", "ul", "li", "blockquote", "pre", "code", "span", "a", "img")
            .addAttributes("a", "href", "target", "rel")
            .addAttributes("img", "src", "alt", "width", "height", "style")
            .addAttributes("span", "class")
            .addAttributes("li", "class", "data-list")
            .addAttributes("p", "class")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https")
            .addEnforcedAttribute("a", "target", "_blank")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    // 이미지 크기 조절 서식만 허용 (예: "width: 300px; height: auto;")
    private static final Pattern IMG_STYLE =
            Pattern.compile("^(\\s*(width|height)\\s*:\\s*(auto|\\d+(px|%))\\s*;?)+\\s*$");

    private final Cleaner cleaner = new Cleaner(SAFELIST);

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        Document clean = cleaner.clean(Jsoup.parseBodyFragment(html));
        clean.select("img[style]").forEach(img -> {
            if (!IMG_STYLE.matcher(img.attr("style")).matches()) {
                img.removeAttr("style");
            }
        });
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }

    public boolean containsImage(String sanitizedHtml) {
        return sanitizedHtml != null && sanitizedHtml.contains("<img");
    }
}
