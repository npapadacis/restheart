/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.files;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.db.FileMetadataDAO;
import org.restheart.db.FileMetadataRepository;
import org.restheart.db.OperationResult;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 * A customised and cut down version of the {@link org.restheart.handlers.document.PutDocumentHandler PutDocumentHandler}
 * or {@link org.restheart.handlers.document.PatchDocumentHandler PatchDocumentHandler}, this deals with both PUT and PATCHing
 * of the metadata for a binary file.
 *
 * @author Nath Papadacis {@literal <nath@thirststudios.co.uk>}
 */
public class FileMetadataHandler extends PipedHttpHandler {

    private static final String METADATA = "metadata";
    private static final String FILENAME = "filename";

    private final FileMetadataRepository fileMetadataDAO;

    /**
     * Creates a new instance of PatchFileMetadataHandler
     */
    public FileMetadataHandler() {
        this(null, new FileMetadataDAO());
    }

    public FileMetadataHandler(FileMetadataRepository fileMetadataDAO) {
        super(null);
        this.fileMetadataDAO = fileMetadataDAO;
    }

    public FileMetadataHandler(PipedHttpHandler next) {
        super(next);
        this.fileMetadataDAO = new FileMetadataDAO();
    }

    public FileMetadataHandler(PipedHttpHandler next, FileMetadataRepository fileMetadataDAO) {
        super(next);
        this.fileMetadataDAO = fileMetadataDAO;
    }

    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        BsonValue _content = context.getContent();

        // cannot proceed with no data
        if (_content == null) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange, context);
            return;
        }

        // cannot proceed with an array
        if (!_content.isDocument()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "data must be a json object");
            next(exchange, context);
            return;
        }

        if (_content.asDocument().isEmpty()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "no data provided");
            next(exchange, context);
            return;
        }

        BsonDocument content = _content.asDocument();
        if (content.get(METADATA) == null) {
            content = new BsonDocument(METADATA, content);
        }
        final BsonValue filename = content.get(METADATA).asDocument().get(FILENAME);
        if (filename != null) {
            content.put(FILENAME, filename);
        }

        BsonValue id = context.getDocumentId();

        if (content.get("_id") == null) {
            content.put("_id", id);
        } else if (!content.get("_id").equals(id)) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "_id in json data cannot be different than id in URL");
            next(exchange, context);
            return;
        }

        OperationResult result = fileMetadataDAO.updateMetadata(
                context.getDBName(),
                context.getCollectionName(),
                context.getDocumentId(),
                context.getFiltersDocument(),
                context.getShardKey(),
                content,
                context.getETag(),
                context.getMethod() == METHOD.PATCH,
                context.isETagCheckRequired());

        context.setDbOperationResult(result);

        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }

        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_CONFLICT,
                    "The document's ETag must be provided using the '"
                            + Headers.IF_MATCH
                            + "' header");
            next(exchange, context);
            return;
        }

        // handle the case of duplicate key error
        if (result.getHttpCode() == HttpStatus.SC_EXPECTATION_FAILED) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_CONFLICT,
                    "A duplicate key error occurred. "
                            + "The patched document does not fulfill "
                            + "an unique index constraint");

            next(exchange, context);
            return;
        }

        context.setResponseStatusCode(result.getHttpCode());

        next(exchange, context);
    }
}