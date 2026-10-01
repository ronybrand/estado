package br.com.rony.spring.boot.estado.config;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;

import br.com.rony.spring.boot.estado.property.RateLimitProperty;

// Extraido do RateLimitFilter (ver ADR 0016) pra ser reutilizado por quem
// mais precisa do IP real do visitante atras de um BFF que nao preserva o
// X-Forwarded-For original (CloudFront, Vercel) - ver ADR 0013. Antes desta
// extracao, AskProxyController usava getRemoteAddr() direto e repassava o IP
// de borda do CloudFront ao estado-ai-agent em vez do visitante real,
// agrupando todo mundo que passa pelo mesmo no de borda no mesmo bucket de
// rate limit.
//
// Nao e @Component: o bean e declarado em WebConfig (@Bean), que ja e
// auto-detectado por @WebMvcTest (WebMvcConfigurer) em todo slice de teste
// que tambem auto-detecta RateLimitFilter (Filter) - um @Component aqui
// ficaria ausente nos slices que nao o importam explicitamente, quebrando
// RateLimitFilter neles.
public class ClientIpResolver {

    private static final String CLIENT_IP_HEADER = "X-Client-IP";
    private static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";

    private final RateLimitProperty rateLimitProperty;

    public ClientIpResolver(RateLimitProperty rateLimitProperty) {
        this.rateLimitProperty = rateLimitProperty;
    }

    public String resolve(HttpServletRequest request) {
        String ipDeclarado = request.getHeader(CLIENT_IP_HEADER);
        if (ipDeclarado != null && segredoConfere(request.getHeader(PROXY_SECRET_HEADER)) && isIpLiteral(ipDeclarado)) {
            return ipDeclarado;
        }
        return request.getRemoteAddr();
    }

    private boolean segredoConfere(String segredoRecebido) {
        String segredoEsperado = rateLimitProperty.getProxySecret();
        if (segredoEsperado == null || segredoEsperado.isBlank() || segredoRecebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                segredoEsperado.getBytes(StandardCharsets.UTF_8), segredoRecebido.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isIpLiteral(String valor) {
        try {
            InetAddress.ofLiteral(valor);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}
