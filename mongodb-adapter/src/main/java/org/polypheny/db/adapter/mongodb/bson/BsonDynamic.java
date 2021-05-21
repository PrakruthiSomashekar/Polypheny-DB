/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.adapter.mongodb.bson;

import lombok.Getter;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.type.PolyType;

public class BsonDynamic extends BsonDocument {

    @Getter
    private final long id;
    private final String polyTypeName;
    private boolean isRegex;


    /**
     * @param id
     * @param polyTypeName
     */
    public BsonDynamic( long id, String polyTypeName ) {
        this( id, polyTypeName, false );
    }


    public BsonDynamic( RexDynamicParam rexNode ) {
        this( rexNode, false );
    }


    public BsonDynamic( RexDynamicParam rexNode, boolean isRegex ) {
        this( rexNode.getIndex(),
                rexNode.getType().getPolyType() == PolyType.ARRAY
                        ? rexNode.getType().getComponentType().getPolyType().getTypeName()
                        : rexNode.getType().getPolyType().getTypeName(), isRegex );
    }


    public BsonDynamic( long id, String polyTypeName, boolean isRegex ) {
        super();
        this.id = id;
        this.polyTypeName = polyTypeName;
        this.isRegex = isRegex;
        append( "_dyn", new BsonInt64( id ) );
        append( "_type", new BsonString( polyTypeName ) );
        append( "_reg", new BsonBoolean( false ) );
    }

}