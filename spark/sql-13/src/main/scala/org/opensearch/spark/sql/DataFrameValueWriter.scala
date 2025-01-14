/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */
 
/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.opensearch.spark.sql

import java.sql.Date
import java.sql.Timestamp
import java.util.{Map => JMap}
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.{Map => SMap}
import scala.collection.Seq
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{ArrayType, DataType, DataTypes, MapType, StructType}
import org.apache.spark.sql.types.DataTypes.BinaryType
import org.apache.spark.sql.types.DataTypes.BooleanType
import org.apache.spark.sql.types.DataTypes.ByteType
import org.apache.spark.sql.types.DataTypes.DateType
import org.apache.spark.sql.types.DataTypes.DoubleType
import org.apache.spark.sql.types.DataTypes.FloatType
import org.apache.spark.sql.types.DataTypes.IntegerType
import org.apache.spark.sql.types.DataTypes.LongType
import org.apache.spark.sql.types.DataTypes.ShortType
import org.apache.spark.sql.types.DataTypes.StringType
import org.apache.spark.sql.types.DataTypes.TimestampType
import org.opensearch.hadoop.cfg.ConfigurationOptions.OPENSEARCH_SPARK_DATAFRAME_WRITE_NULL_VALUES_DEFAULT
import org.opensearch.hadoop.serialization.builder.ValueWriter.Result
import org.opensearch.hadoop.cfg.Settings
import org.opensearch.hadoop.serialization.{OpenSearchHadoopSerializationException, Generator, SettingsAware}
import org.opensearch.hadoop.serialization.builder.FilteringValueWriter
import org.opensearch.hadoop.util.unit.Booleans


class DataFrameValueWriter(writeUnknownTypes: Boolean = false) extends FilteringValueWriter[Any] with SettingsAware {

  def this() {
    this(false)
  }

  private var writeNullValues: Boolean = Booleans.parseBoolean(OPENSEARCH_SPARK_DATAFRAME_WRITE_NULL_VALUES_DEFAULT)

  override def setSettings(settings: Settings): Unit = {
    super.setSettings(settings)
    writeNullValues = settings.getDataFrameWriteNullValues
  }

  override def write(value: Any, generator: Generator): Result = {
    value match {
      case Tuple2(row, schema: StructType) =>
        writeStruct(schema, row, generator)
      case map: Map[_, _] =>
        writeMapWithInferredSchema(map, generator)
      case seq: Seq[Row] =>
        writeArray(seq, generator)
    }
  }

  private[spark] def writeArray(value: Seq[Row], generator: Generator): Result = {
    if (value.nonEmpty) {
      val schema = value.head.schema
      val result = write(DataTypes.createArrayType(schema), value, generator)
      if (!result.isSuccesful) {
        return handleUnknown(value, generator)
      }
    } else {
      generator.writeBeginArray().writeEndArray()
    }
    Result.SUCCESFUL()
  }

  private[spark] def writeStruct(schema: StructType, value: Any, generator: Generator): Result = {
    value match {
      case r: Row =>
        generator.writeBeginObject()

        schema.fields.view.zipWithIndex foreach {
          case (field, index) =>
            if (shouldKeep(generator.getParentPath,field.name)) {
              if (!r.isNullAt(index)) {
                generator.writeFieldName(field.name)
                val result = write(field.dataType, r(index), generator)
                if (!result.isSuccesful) {
                  return handleUnknown(value, generator)
                }
              } else if (writeNullValues) {
                generator.writeFieldName(field.name)
                generator.writeNull()
              }
            }
        }
        generator.writeEndObject()

        Result.SUCCESFUL()
    }
  }

  private[spark] def write(schema: DataType, value: Any, generator: Generator): Result = {
    schema match {
      case s @ StructType(_)    => writeStruct(s, value, generator)
      case a @ ArrayType(_, _)  => writeArray(a, value, generator)
      case m @ MapType(_, _, _) => writeMap(m, value, generator)
      case _                    => writePrimitive(schema, value, generator)
    }
  }

  private[spark] def writeArray(schema: ArrayType, value: Any, generator: Generator): Result = {
    value match {
      case a: Array[_] => doWriteSeq(schema.elementType, a, generator)
      case s: Seq[_]   => doWriteSeq(schema.elementType, s, generator)
      // unknown array type
      case _           => handleUnknown(value, generator)
    }
  }

  private def doWriteSeq(schema: DataType, value: Seq[_], generator: Generator): Result = {
    generator.writeBeginArray()
    if (value != null) {
      value.foreach { v =>
        val result = write(schema, v, generator)
        if (!result.isSuccesful()) {
          return handleUnknown(value, generator)
        }
      }
    }
    generator.writeEndArray()
    Result.SUCCESFUL()
  }

  private[spark] def writeMap(schema: MapType, value: Any, generator: Generator): Result = {
    value match {
      case sm: SMap[_, _] => doWriteMap(schema, sm, generator)
      case jm: JMap[_, _] => doWriteMap(schema, jm.asScala, generator)
      // unknown map type
      case _              => handleUnknown(value, generator)
    }
  }

  private def doWriteMap(schema: MapType, value: SMap[_, _], generator: Generator): Result = {
    generator.writeBeginObject()

    if (value != null) {
      for ((k, v) <- value) {
        if (shouldKeep(generator.getParentPath(), k.toString())) {
          generator.writeFieldName(k.toString)
          val result = write(schema.valueType, v, generator)
          if (!result.isSuccesful()) {
            return handleUnknown(v, generator)
          }
        }
      }
    }

    generator.writeEndObject()
    Result.SUCCESFUL()
  }

  private def writeMapWithInferredSchema(value: Any, generator: Generator): Result = {
    value match {
      case sm: SMap[_, _] => doWriteMapWithInferredSchema(sm, generator)
      case jm: JMap[_, _] => doWriteMapWithInferredSchema(jm.asScala, generator)
      // unknown map type
      case _              => handleUnknown(value, generator)
    }
  }

  private def doWriteMapWithInferredSchema(map: SMap[_, _], generator: Generator): Result = {
    if (map != null && map.valuesIterator.hasNext) {
      val sampleValueOption = getFirstNotNullElement(map.valuesIterator)
      val schema = inferMapSchema(sampleValueOption)
      doWriteMap(schema, map, generator)
    } else {
      writeEmptyMap(generator)
    }
  }

  private def writeEmptyMap(generator: Generator): Result = {
    generator.writeBeginObject().writeEndObject()
    Result.SUCCESFUL()
  }

  private def inferMapSchema(valueOption: Option[Any]): MapType = {
    if(valueOption.isDefined) {
      val valueType = inferType(valueOption.get)
      MapType(StringType, valueType) //The key type is never read
    } else {
      MapType(StringType, StringType) //Does not matter if the map is empty or has no values
    }
  }

  def inferArraySchema(array: Array[_]): DataType = {
    val EMPTY_ARRAY_TYPE = StringType  //Makes no difference for an empty array
    if (array.isEmpty) {
      EMPTY_ARRAY_TYPE
    } else {
      val sampleValueOption = getFirstNotNullElement(array.iterator)
      if (sampleValueOption.isDefined) {
        inferType(sampleValueOption.get)
      }
      else {
        EMPTY_ARRAY_TYPE
      }
    }
  }

  def getFirstNotNullElement(iterator: Iterator[_]): Option[Any] = {
    iterator.find(value => Option(value).isDefined)
  }

  private def inferType(value: Any): DataType = {
    value match {
      case _: String               => StringType
      case _: Int                  => IntegerType
      case _: Integer              => IntegerType
      case _: Boolean              => BooleanType
      case _: java.lang.Boolean    => BooleanType
      case _: Short                => ShortType
      case _: java.lang.Short      => ShortType
      case _: Long                 => LongType
      case _: java.lang.Long       => LongType
      case _: Double               => DoubleType
      case _: java.lang.Double     => DoubleType
      case _: Float                => FloatType
      case _: java.lang.Float      => FloatType
      case _: Timestamp            => TimestampType
      case _: Date                 => DateType
      case _: Array[Byte]          => BinaryType
      case array: Array[_]         => ArrayType(inferArraySchema(array))
      case map: Map[_, _]          => inferMapSchema(getFirstNotNullElement(map.valuesIterator))
    }
  }

  private[spark] def writePrimitive(schema: DataType, value: Any, generator: Generator): Result = {
    if (value == null) {
      generator.writeNull()
    }
    else schema match {
      case BinaryType    => generator.writeBinary(value.asInstanceOf[Array[Byte]])
      case BooleanType   => generator.writeBoolean(value.asInstanceOf[Boolean])
      case ByteType      => generator.writeNumber(value.asInstanceOf[Byte])
      case ShortType     => generator.writeNumber(value.asInstanceOf[Short])
      case IntegerType   => generator.writeNumber(value.asInstanceOf[Int])
      case LongType      => generator.writeNumber(value.asInstanceOf[Long])
      case DoubleType    => generator.writeNumber(value.asInstanceOf[Double])
      case FloatType     => generator.writeNumber(value.asInstanceOf[Float])
      case TimestampType => generator.writeNumber(value.asInstanceOf[Timestamp].getTime())
      case DateType      => generator.writeNumber(value.asInstanceOf[Date].getTime())
      case StringType    => generator.writeString(value.toString)
      case _             => {
        val className = schema.getClass().getName()
        if ("org.apache.spark.sql.types.DecimalType".equals(className) || "org.apache.spark.sql.catalyst.types.DecimalType".equals(className)) {
          throw new OpenSearchHadoopSerializationException("Decimal types are not supported by OpenSearch - consider using a different type (such as string)")
        }
        return handleUnknown(value, generator)
      }
    }

    Result.SUCCESFUL()
  }

  protected def handleUnknown(value: Any, generator: Generator): Result = {
    if (!writeUnknownTypes) {
      println("can't handle type " + value);
      Result.FAILED(value)
    } else {
      generator.writeString(value.toString())
      Result.SUCCESFUL()
    }
  }
}