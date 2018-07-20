package com.uber.stream.kafka.mirrormaker.controller.rest.resources;

import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * Health check servlet.
 */
// TODO: 2018/6/15 by zmyer
public class HealthCheckRestletResource extends ServerResource {

    @Override
    @Get
    public Representation get() {
        return new StringRepresentation("OK");
    }

}
