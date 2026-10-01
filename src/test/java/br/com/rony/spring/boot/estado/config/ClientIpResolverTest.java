package br.com.rony.spring.boot.estado.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import br.com.rony.spring.boot.estado.property.RateLimitProperty;

class ClientIpResolverTest {

    private HttpServletRequest requisicaoViaProxy(String ipRemoto, String ipDoCliente, String segredo) {
        HttpServletRequest requisicao = mock(HttpServletRequest.class);
        when(requisicao.getRemoteAddr()).thenReturn(ipRemoto);
        when(requisicao.getHeader("X-Client-IP")).thenReturn(ipDoCliente);
        when(requisicao.getHeader("X-Proxy-Secret")).thenReturn(segredo);
        return requisicao;
    }

    private ClientIpResolver resolverComSegredo(String segredo) {
        RateLimitProperty property = new RateLimitProperty();
        property.setProxySecret(segredo);
        return new ClientIpResolver(property);
    }

    @Test
    void usaIpDoHeaderQuandoSegredoConfere() {
        ClientIpResolver resolver = resolverComSegredo("s3cret");

        String ip = resolver.resolve(requisicaoViaProxy("76.76.21.21", "203.0.113.1", "s3cret"));

        assertEquals("203.0.113.1", ip);
    }

    @Test
    void ignoraHeaderQuandoSegredoErrado() {
        ClientIpResolver resolver = resolverComSegredo("s3cret");

        String ip = resolver.resolve(requisicaoViaProxy("76.76.21.21", "203.0.113.1", "errado"));

        assertEquals("76.76.21.21", ip);
    }

    @Test
    void ignoraHeaderQuandoNenhumSegredoConfigurado() {
        ClientIpResolver resolver = resolverComSegredo("");

        String ip = resolver.resolve(requisicaoViaProxy("76.76.21.21", "203.0.113.1", ""));

        assertEquals("76.76.21.21", ip);
    }

    @Test
    void ignoraHeaderQueNaoEUmIpLiteral() {
        ClientIpResolver resolver = resolverComSegredo("s3cret");

        String ip = resolver.resolve(requisicaoViaProxy("76.76.21.21", "nao-e-um-ip", "s3cret"));

        assertEquals("76.76.21.21", ip);
    }

    @Test
    void usaIpDaConexaoQuandoHeaderAusente() {
        ClientIpResolver resolver = resolverComSegredo("s3cret");

        String ip = resolver.resolve(requisicaoViaProxy("76.76.21.21", null, null));

        assertEquals("76.76.21.21", ip);
    }

    // X-Client-IP presente mas sem X-Proxy-Secret, com um segredo configurado
    // no backend - branch distinto do "nenhum segredo configurado" acima
    // (aqui o esperado existe, so o recebido que falta).
    @Test
    void ignoraHeaderQuandoSegredoConfiguradoMasNaoEnviado() {
        ClientIpResolver resolver = resolverComSegredo("s3cret");

        String ip = resolver.resolve(requisicaoViaProxy("76.76.21.21", "203.0.113.1", null));

        assertEquals("76.76.21.21", ip);
    }
}
