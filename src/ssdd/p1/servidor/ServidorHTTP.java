package ssdd.p1.servidor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;

import ssdd.p1.herramientas.HTTPParser;
import ssdd.p1.herramientas.Utiles;

public abstract class ServidorHTTP {

    @SuppressWarnings("rawtypes")
    protected static String httpGet(HTTPParser parser)
            throws FileNotFoundException {
        return httpGet(parser, null);
    }

    @SuppressWarnings("rawtypes")
    protected static String httpGet(HTTPParser parser, Utiles util)
            throws FileNotFoundException {

        File path = new File("");
        File fichero = new File(path.getAbsolutePath() + parser.getPath());

        if (!fichero.exists()) {

            // NOT FOUND (404)
            return Utiles.generaRespuesta(404);
        } else {
            Matcher matcher = Utiles.patronRutaFichero
                    .matcher(parser.getPath());
            if (fichero.isFile() && matcher.matches()) {

                // OK (200)
                
                return Utiles.generaRespuesta(200, fichero);

            } else {

                // FORBIDDEN (403)
                return Utiles.generaRespuesta(403);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    protected static String httpPost(HTTPParser parser)
            throws UnsupportedEncodingException {

        ByteBuffer bodyBuf = parser.getBody();
        String body = "";

        if (bodyBuf.hasArray()) {
            body = new String(bodyBuf.array());
        }

        // separar los parametros antes de decodificar por si se
        // incluye el caracter '&' en el contenido de alguno
        String[] params = body.split("&");

        if (params.length == 2) {
            params[0] = URLDecoder.decode(params[0], "UTF-8");
            params[1] = URLDecoder.decode(params[1], "UTF-8");

            String nomP1 = params[0].substring(0, params[0].indexOf("="));
            String nomP2 = params[1].substring(0, params[1].indexOf("="));

            if (nomP1.compareTo("fname") == 0
                    && nomP2.compareTo("content") == 0) {

                String contP1 = params[0].substring(params[0].indexOf("=") + 1);
                Matcher matcher = Utiles.patronRutaFichero.matcher(contP1);

                if (matcher.matches()) {
                    String contP2 = params[1]
                            .substring(params[1].indexOf("=") + 1);

                    Utiles.escribeFichero(contP1, contP2);

                    contP2 = Utiles.reEncode(contP2);

                    return Utiles.generaRespuesta(200,
                            Utiles.generaCuerpoExito(contP1, contP2));
                } else {

                    // FORBIDDEN (403)
                    return Utiles.generaRespuesta(403);
                }
            } else {

                // BAD REQUEST (400)
                return Utiles.generaRespuesta(400);
            }
        } else {

            // BAD REQUEST (400)
            return Utiles.generaRespuesta(400);
        }
    }

}