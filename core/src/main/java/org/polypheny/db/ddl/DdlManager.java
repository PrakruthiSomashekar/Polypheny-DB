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

package org.polypheny.db.ddl;


import java.util.List;
import java.util.Map;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.catalog.Catalog.ForeignKeyOption;
import org.polypheny.db.catalog.Catalog.SchemaType;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.catalog.exceptions.ColumnAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.SchemaAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.TableAlreadyExistsException;
import org.polypheny.db.catalog.exceptions.UnknownAdapterException;
import org.polypheny.db.catalog.exceptions.UnknownColumnException;
import org.polypheny.db.catalog.exceptions.UnknownDatabaseException;
import org.polypheny.db.catalog.exceptions.UnknownKeyException;
import org.polypheny.db.catalog.exceptions.UnknownSchemaException;
import org.polypheny.db.catalog.exceptions.UnknownTableException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.ddl.exception.AlterSourceException;
import org.polypheny.db.ddl.exception.DdlOnSourceException;
import org.polypheny.db.ddl.exception.IndexExistsException;
import org.polypheny.db.ddl.exception.IndexPreventsRemovalException;
import org.polypheny.db.ddl.exception.LastPlacementException;
import org.polypheny.db.ddl.exception.MissingColumnPlacementException;
import org.polypheny.db.ddl.exception.NotNullAndDefaultValueException;
import org.polypheny.db.ddl.exception.PlacementAlreadyExistsException;
import org.polypheny.db.ddl.exception.PlacementIsPrimaryException;
import org.polypheny.db.ddl.exception.PlacementNotExistsException;
import org.polypheny.db.ddl.exception.UnknownIndexMethodException;
import org.polypheny.db.processing.QueryProcessor;
import org.polypheny.db.routing.Router;
import org.polypheny.db.sql.SqlDataTypeSpec;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.TransactionException;

/**
 * Abstract class for the DDLManager, goal of this class is to expose a unified interface,
 * which allows to handle ddls. Especially with regard to different models.
 * The ddl methods should contain all logic needed for them and throw appropriate Exceptions
 */
public abstract class DdlManager {

    public static DdlManager INSTANCE = null;


    public enum Language {
        SQL( 1 ),
        MQL( 2 );

        private final int id;


        Language( int id ) {
            this.id = id;
        }
    }


    /**
     * Sets a new DdlManager and returns it.
     *
     * @param manager the DdlManager which is set
     * @return the set instance of the DdlManager, which was set
     */
    public static DdlManager setAndGetInstance( DdlManager manager ) {
        if ( INSTANCE != null ) {
            throw new RuntimeException( "Overwriting the DdlManger, when already set is not permitted." );
        }
        INSTANCE = manager;
        return INSTANCE;
    }


    /**
     * Access Pattern for DdlManager Singleton.
     *
     * @return The DdlManager
     */
    public static DdlManager getInstance() {
        if ( INSTANCE == null ) {
            throw new RuntimeException( "DdlManager was not set correctly on Polypheny-DB start-up" );
        }
        return INSTANCE;
    }


    /**
     * Creates a schema with the provided options.
     *
     * @param name name of the desired schema
     * @param databaseId id of the database, to which the schema belongs
     * @param type the schema type, RELATIONAL, DOCUMENT, etc.
     * @param userId the id of executing user
     * @param ifNotExists if the schema only needs to be created when it not already exists
     * @param replace if the schema should replace another
     */
    public abstract void createSchema( String name, long databaseId, SchemaType type, int userId, boolean ifNotExists, boolean replace ) throws SchemaAlreadyExistsException;

    /**
     * Adds a new adapter with the name and the config options given to a store
     *
     * @param storeName name of the store for the adapter
     * @param adapterName the name of the adapter itself
     * @param config all provided options
     */
    public abstract void addAdapter( String storeName, String adapterName, Map<String, String> config );

    /**
     * Drops an adapter
     *
     * @param name name of the adapter to drop
     * @param router
     * @param processor
     */
    public abstract void dropAdapter( String name, Router router, QueryProcessor processor ) throws UnknownAdapterException;


    /**
     * Change the owner of a specific schema
     *
     * @param databaseId the id of the database of the schema
     * @param schemaName the name of the desired schema
     * @param ownerName the name of the owner
     */
    public abstract void alterSchemaOwner( String schemaName, String ownerName, long databaseId ) throws UnknownUserException, UnknownSchemaException;

    /**
     * Change the name of the specific schema
     *
     * @param newName the new name of the schema
     * @param oldName the old name of the schema
     * @param databaseId the id of the database the schema belongs to
     */
    public abstract void alterSchemaRename( String newName, String oldName, long databaseId ) throws SchemaAlreadyExistsException, UnknownSchemaException;

    /**
     * Adds a column to an existing source table
     *
     * @param catalogTable the target table
     * @param columnPhysicalName the physical name of the new column
     * @param columnLogicalName the name of the new column
     * @param beforeColumn the column before the column which is inserted; can be null
     * @param afterColumn the column after the column, which is inserted; can be null
     * @param defaultValue the default value of the inserted column
     */
    public abstract void alterSourceTableAddColumn( CatalogTable catalogTable, String columnPhysicalName, String columnLogicalName, CatalogColumn beforeColumn, CatalogColumn afterColumn, SqlNode defaultValue, Statement statement ) throws ColumnAlreadyExistsException;

    /**
     * Adds a column to an existing table
     *
     * @param columnName the name of the new column
     * @param catalogTable the target table
     * @param beforeColumn the column before the added column; can be null
     * @param afterColumn the column after the added column; can be null
     * @param type the SQL data type specification of the new column
     * @param nullable defines if the column can hold NULL values
     * @param defaultValue provides a default value for the column
     * @param statement the query statement
     */
    public abstract void alterTableAddColumn( String columnName, CatalogTable catalogTable, CatalogColumn beforeColumn, CatalogColumn afterColumn, SqlDataTypeSpec type, boolean nullable, SqlNode defaultValue, Statement statement ) throws NotNullAndDefaultValueException, ColumnAlreadyExistsException;

    /**
     * Adds foreign keys to a table
     *
     * @param catalogTable the target table
     * @param refTable the catalog table, to which the foreign keys references
     * @param columnNames the names of the columns in the table
     * @param refColumnNames the names of the columns which are referenced by the keys
     * @param columnListPos the position of the column list in the query
     * @param constraintName the name of this new foreign key constraint
     * @param onUpdate the onUpdate function
     * @param onDelete ht onDelete function
     */
    public abstract void alterTableAddForeignKey( CatalogTable catalogTable, CatalogTable refTable, List<String> columnNames, List<String> refColumnNames, SqlParserPos columnListPos, String constraintName, ForeignKeyOption onUpdate, ForeignKeyOption onDelete ) throws UnknownColumnException, GenericCatalogException;

    /**
     * Adds a index to the table
     *
     * @param catalogTable the table to which a index should be added
     * @param indexMethodName name of the indexMethod; can be null
     * @param columnNames names of all columns, which belong to the index
     * @param indexName name of the index
     * @param isUnique if the index is unique
     * @param storeInstance the instance of the store; can be null
     * @param statement the initial query statement
     */
    public abstract void alterTableAddIndex( CatalogTable catalogTable, String indexMethodName, List<String> columnNames, String indexName, boolean isUnique, DataStore storeInstance, Statement statement ) throws UnknownColumnException, UnknownIndexMethodException, GenericCatalogException, UnknownTableException, UnknownUserException, UnknownSchemaException, UnknownKeyException, UnknownDatabaseException, TransactionException, AlterSourceException, IndexExistsException, MissingColumnPlacementException;


    /**
     * Adds new column placements to a table
     *
     * @param catalogTable the target table
     * @param columnIds the ids of the columns which should be placed
     * @param dataStore the target store for the placements
     * @param statement the query statement
     */
    public abstract void alterTableAddPlacement( CatalogTable catalogTable, List<Long> columnIds, DataStore dataStore, Statement statement ) throws PlacementAlreadyExistsException;

    /**
     * Adds a new primary key to a table
     *
     * @param catalogTable the target table
     * @param columnNames the names of all columns in the primary key
     * @param statement the query statement
     */
    public abstract void alterTableAddPrimaryKey( CatalogTable catalogTable, List<String> columnNames, Statement statement ) throws DdlOnSourceException;

    /**
     * Adds a unique constraint to a table
     *
     * @param catalogTable the target table
     * @param columnNames the names of the columns, which a part of the constraint
     * @param constraintName the name of the unique constraint
     */
    public abstract void alterTableAddUniqueConstraint( CatalogTable catalogTable, List<String> columnNames, String constraintName ) throws DdlOnSourceException;

    /**
     * Drop a specific column in a table
     *
     * @param catalogTable the target table
     * @param catalogColumn the column which is dropped
     * @param statement the query statement
     */
    public abstract void alterTableDropColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, Statement statement );

    /**
     * Drop a specific constraint from a table
     *
     * @param catalogTable the target table
     * @param constraintName the name of the constraint, which is dropped
     */
    public abstract void alterTableDropConstraint( CatalogTable catalogTable, String constraintName ) throws DdlOnSourceException;

    /**
     * Drops a foreign key of a table
     *
     * @param catalogTable the target table
     * @param foreignKeyName the name of the foreign key to drop
     */
    public abstract void alterTableDropForeignKey( CatalogTable catalogTable, String foreignKeyName ) throws DdlOnSourceException;

    /**
     * Drops specific indexes on a table
     *
     * @param catalogTable the target table
     * @param indexName the name of the index to drop
     * @param statement the query statement
     */
    public abstract void alterTableDropIndex( CatalogTable catalogTable, String indexName, Statement statement ) throws DdlOnSourceException;

    /**
     * Drop a specific placement of a table
     *
     * @param catalogTable the target table
     * @param storeInstance the store in which the placement was placed, and gets dropped
     * @param statement the query statement
     */
    public abstract void alterTableDropPlacement( CatalogTable catalogTable, DataStore storeInstance, Statement statement ) throws PlacementNotExistsException, LastPlacementException;

    /**
     * Drop the primary key of a table
     *
     * @param catalogTable the target table
     */
    public abstract void alterTableDropPrimaryKey( CatalogTable catalogTable ) throws DdlOnSourceException;

    /**
     * Modifies a table
     *
     * @param catalogTable the target table
     * @param catalogColumn the specific column, which is modified
     * @param type the sql specific type
     * @param collation
     * @param defaultValue the default value of the column
     * @param nullable if the column is nullable
     * @param dropDefault if the default is dropped
     * @param beforeColumn the column before the modified column
     * @param afterColumn the column after the modified column
     * @param statement the used statement
     */
    public abstract void alterTableModifyColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, SqlDataTypeSpec type, String collation, SqlNode defaultValue, Boolean nullable, Boolean dropDefault, CatalogColumn beforeColumn, CatalogColumn afterColumn, Statement statement ) throws DdlOnSourceException;

    /**
     * Modifies a placement in a table
     *
     * @param catalogTable the target table
     * @param columnIds the ids of the involved columns
     * @param storeInstance the instance on which the placements are
     * @param statement the used statement
     */
    public abstract void alterTableModifyPlacement( CatalogTable catalogTable, List<Long> columnIds, DataStore storeInstance, Statement statement ) throws PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException;

    /**
     * Modifies a placement and adds column to it on a specific store
     *
     * @param catalogTable the target table
     * @param catalogColumn the column which is placed
     * @param storeInstance the store on which the column is placed
     * @param statement the used statement
     */
    public abstract void alterTableModifyPlacementAndColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, PlacementAlreadyExistsException;

    /**
     * Changes the table placements by dropping a column from it
     *
     * @param catalogTable the target table
     * @param catalogColumn the columns for which the placement is dropped
     * @param storeInstance the store in which the column placement is dropped
     * @param statement the used statement
     */
    public abstract void alterTableModifyPlacementDropColumn( CatalogTable catalogTable, CatalogColumn catalogColumn, DataStore storeInstance, Statement statement ) throws UnknownAdapterException, PlacementNotExistsException, IndexPreventsRemovalException, LastPlacementException, PlacementIsPrimaryException;

    /**
     * Changes the owner of a table
     *
     * @param catalogTable the target table
     * @param newOwnerName the name of the new owner
     */
    public abstract void alterTableOwner( CatalogTable catalogTable, String newOwnerName ) throws UnknownUserException;

    /**
     * Renames a table
     *
     * @param catalogTable the target table
     * @param newTableName the new name for the table
     * @param statement the used statement
     */
    public abstract void alterTableRename( CatalogTable catalogTable, String newTableName, Statement statement ) throws TableAlreadyExistsException;

    /**
     * Renames a column in a table
     *
     * @param catalogColumn the target column, which is renamed
     * @param newColumnName the new name of the column
     * @param statement the used statement
     */
    public abstract void renameColumn( CatalogColumn catalogColumn, String newColumnName, Statement statement );
}
