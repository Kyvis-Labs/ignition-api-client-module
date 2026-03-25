package net.dongliu.requests.body;

import net.dongliu.requests.utils.URLUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static net.dongliu.requests.HttpHeaders.CONTENT_TYPE_FORM_ENCODED;

/**
 * @author Liu Dong
 */
class FormRequestBody extends RequestBody<Collection<? extends Map.Entry<String, ?>>> {
    private static final long serialVersionUID = 6322052512305107136L;

    FormRequestBody(Collection<? extends Map.Entry<String, ?>> body) {
        super(body, CONTENT_TYPE_FORM_ENCODED, true);
    }

    @Override
    public void writeBody(OutputStream out, Charset charset) throws IOException {
        String content = URLUtils.encodeForms(URLUtils.toStringParameters(body()), charset);
        try (Writer writer = new OutputStreamWriter(out, charset)) {
            writer.write(content);
        }
    }

}
