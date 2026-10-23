import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

// Maven does not automatically use HTTP_PROXY/HTTPS_PROXY like curl and npm do.
class ProxySettings {
    public static void main(String[] args) throws Exception {
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null || proxy.isBlank()) proxy = System.getenv("HTTP_PROXY");
        if (proxy == null || proxy.isBlank()) {
            Files.writeString(Path.of(args[0]), "<settings/>");
            return;
        }
        URI uri = URI.create(proxy);
        if (uri.getHost() == null) throw new IllegalArgumentException("Build proxy needs a valid hostname");
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        String auth = "";
        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            auth = "<username>" + xml(parts[0]) + "</username><password>" + xml(parts.length > 1 ? parts[1] : "") + "</password>";
        }
        Files.writeString(Path.of(args[0]), "<settings><proxies><proxy><id>build</id><active>true</active>"
                + "<protocol>http</protocol><host>" + xml(uri.getHost()) + "</host><port>" + port + "</port>"
                + auth + "<nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies></settings>");
    }
    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
