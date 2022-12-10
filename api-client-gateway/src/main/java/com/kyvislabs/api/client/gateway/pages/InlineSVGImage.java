package com.kyvislabs.api.client.gateway.pages;

import org.apache.commons.codec.binary.Base64;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebComponent;
import org.apache.wicket.model.IModel;

public class InlineSVGImage extends WebComponent {

    private static final long serialVersionUID = 1L;

    public InlineSVGImage(String id, IModel<byte[]> model) {
        super(id, model);
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        checkComponentTag(tag, "img");
        super.onComponentTag(tag);

        byte[] data = (byte[]) getDefaultModel().getObject();
        if (data != null) {
            try {
                StringBuilder builder = new StringBuilder();
                builder.append("data:");
                builder.append("image/svg+xml");
                builder.append(";base64,");
                builder.append(Base64.encodeBase64String(data));
                tag.put("src", builder.toString());
            } catch (Exception e) {
                throw new WicketRuntimeException("An error occured while reading the package resource stream", e);
            }
        } else {
            // If the package resource stream is not set create an empty image
            tag.put("src", "#");
            tag.put("style", "display:none;");
        }
    }
}
