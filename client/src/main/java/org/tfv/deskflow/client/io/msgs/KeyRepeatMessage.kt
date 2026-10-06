/*
 * MIT License
 *
 * Copyright (c) 2025 Jonathan Glanz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
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
 */
package org.tfv.deskflow.client.io.msgs

import org.tfv.deskflow.client.io.readString
import org.tfv.deskflow.client.io.writeString
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * DKRP 1.0:  3 × short  = 6 字节（id, mask, count）
 * DKRP 新:   4 × short + 可选 UTF-8 string（id, mask, count, button, text）
 * 模板在 MessageTemplate 里会命中新版（有 %s），但服务端可能发老版。
 * readData 根据 dataSize 智能判断，避免越界读。
 */
class KeyRepeatMessage(
    var id: UInt = 0u,
    var mask: UInt = 0u,
    var count: Short = 0,
    var button: UInt = 0u,
    var text: String? = null,
) : Message(MESSAGE_TYPE) {

    override fun readData(inStream: DataInputStream, dataSize: Int) {
        id = inStream.readUnsignedShort().toUInt()
        mask = inStream.readUnsignedShort().toUInt()
        count = inStream.readShort()
        if (dataSize >= 8) {
            // 新版 DKRP：多一个 button + 可选 text
            button = inStream.readUnsignedShort().toUInt()
            if (dataSize > 8) {
                text = inStream.readString()
            }
        }
    }

    override fun writeData(outStream: DataOutputStream) {
        outStream.writeShort(id.toInt())
        outStream.writeShort(mask.toInt())
        outStream.writeShort(count.toInt())
        outStream.writeShort(button.toInt())
        text?.let { outStream.writeString(it) }
    }

    override fun toString(): String {
        val textPart = text?.let { ", text=\"$it\"" } ?: ""
        return "$MESSAGE_TYPE:$id:$mask:$count:$button$textPart"
    }

    companion object {
        val MESSAGE_TYPE: MessageType = MessageType.DKEYREPEAT
    }
}
