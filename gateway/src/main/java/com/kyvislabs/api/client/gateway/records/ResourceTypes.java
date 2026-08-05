package com.kyvislabs.api.client.gateway.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;

public class ResourceTypes {

    public static final String MODULE_ID = "com.kyvislabs.api.client";

    public static final ResourceType API_RESOURCE_TYPE =
            new ResourceType(MODULE_ID, "api");

    public static final ResourceTypeMeta<APIResource> API_RESOURCE_TYPE_META =
            ResourceTypeMeta.newBuilder(APIResource.class)
                    .resourceType(API_RESOURCE_TYPE)
                    .categoryName("API")
                    .buildRouteDelegate(routes -> routes
                            .configSchema(APIResource.class)
                            .openApiGroupName("API Client")
                            .openApiTagName("api")
                    ).build();
}
