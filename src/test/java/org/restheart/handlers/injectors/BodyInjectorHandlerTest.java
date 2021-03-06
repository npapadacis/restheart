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
package org.restheart.handlers.injectors;

import static org.junit.Assert.assertEquals;
import io.undertow.server.handlers.form.FormData;

import java.lang.reflect.Field;

import org.bson.BsonDocument;
import org.junit.Test;
import org.restheart.handlers.PipedHttpHandler;

/**
 *
 * @author Maurizio Turatti
 */
public class BodyInjectorHandlerTest {

    public BodyInjectorHandlerTest() {
    }

    /**
     * If formData contains a PROPERTIES part, then must be valid JSON
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    @Test
    public void test_extractProperties() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final String jsonString
                = "{\"key1\": \"value1\", \"key2\": \"value2\"}";
        FormData formData = new FormData(1);
        Field field = PipedHttpHandler.class.getDeclaredField("PROPERTIES");
        field.setAccessible(true);

        formData.add(field.get(null).toString(), jsonString);
        BsonDocument result = BodyInjectorHandler.extractMetadata(formData);
        BsonDocument expected = BsonDocument.parse(jsonString);
        assertEquals(expected, result);
    }

}
