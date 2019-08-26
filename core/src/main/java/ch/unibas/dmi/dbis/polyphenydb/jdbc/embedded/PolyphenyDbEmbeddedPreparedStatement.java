/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package ch.unibas.dmi.dbis.polyphenydb.jdbc.embedded;


import java.sql.SQLException;
import org.apache.calcite.avatica.AvaticaPreparedStatement;
import org.apache.calcite.avatica.Meta;


/**
 * Implementation of {@link java.sql.PreparedStatement} for the Polypheny-DB engine.
 *
 * This class has sub-classes which implement JDBC 3.0 and JDBC 4.0 APIs; it is instantiated using {@link org.apache.calcite.avatica.AvaticaFactory#newPreparedStatement}.
 */
abstract class PolyphenyDbEmbeddedPreparedStatement extends AvaticaPreparedStatement {

    /**
     * Creates a PolyphenyDbEmbeddedPreparedStatement.
     *
     * @param connection Connection
     * @param h Statement handle
     * @param signature Result of preparing statement
     * @param resultSetType Result set type
     * @param resultSetConcurrency Result set concurrency
     * @param resultSetHoldability Result set holdability
     * @throws SQLException if database error occurs
     */
    protected PolyphenyDbEmbeddedPreparedStatement( PolyphenyDbEmbeddedConnectionImpl connection, Meta.StatementHandle h, Meta.Signature signature, int resultSetType, int resultSetConcurrency, int resultSetHoldability ) throws SQLException {
        super( connection, h, signature, resultSetType, resultSetConcurrency, resultSetHoldability );
    }


    @Override
    public PolyphenyDbEmbeddedConnectionImpl getConnection() throws SQLException {
        return (PolyphenyDbEmbeddedConnectionImpl) super.getConnection();
    }
}