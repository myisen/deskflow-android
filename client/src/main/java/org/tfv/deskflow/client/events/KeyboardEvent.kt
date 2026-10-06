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

package org.tfv.deskflow.client.events

import org.tfv.deskflow.client.models.keys.KeyModifierMask
import java.io.Serializable

<<<<<<< HEAD
data class KeyboardEvent(val type: Type, val id: UInt, val button: UInt = 0u, val mask: UInt, val count: Short = 0) : ClientEvent(), Serializable {
=======
/**
 * KeyboardEvent — 客户端侧键盘事件。
 *
 * `text` 字段携带 Deskflow 服务端 DKDL（KeyDownLang）消息中的 UTF-8 文本，
 * 用于中文、日文等 IME 输入场景。普通 DKDN 事件 text 为 null。
 */
data class KeyboardEvent(
    val type: Type,
    val id: UInt,
    val button: UInt = 0u,
    val mask: UInt,
    val count: Short = 0,
    val text: String? = null,
) : ClientEvent(), Serializable {
>>>>>>> trae/agent-0UI0AO

    enum class Type {
        Up,
        Down,
        Repeat
    }

<<<<<<< HEAD
    fun getModifiers():KeyModifierMask {
=======
    fun getModifiers(): KeyModifierMask {
>>>>>>> trae/agent-0UI0AO
        return KeyModifierMask(mask)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
<<<<<<< HEAD
        return "KeyboardEvent(type=$type, id=($id,${id.toHexString()},${id.toString(2)}), button=$button, mask=($mask,${mask.toHexString()},${mask.toString(2)}), count=$count)"
    }

    companion object {
        fun down(id: UInt, button: UInt = 0u, mask: UInt = 0u) = KeyboardEvent(Type.Down, id, button, mask)
        fun up(id: UInt, button: UInt = 0u, mask: UInt = 0u) = KeyboardEvent(Type.Up, id, button, mask)
        fun repeat(id: UInt, button: UInt = 0u, mask: UInt = 0u, count: Short = 0) = KeyboardEvent(Type.Repeat, id, button, mask,count)
=======
        val textPart = text?.let { ", text=\"$it\"" } ?: ""
        return "KeyboardEvent(type=$type, id=($id,${id.toHexString()},${id.toString(2)}), button=$button, mask=($mask,${mask.toHexString()},${mask.toString(2)}), count=$count$textPart)"
    }

    companion object {
        fun down(
            id: UInt,
            button: UInt = 0u,
            mask: UInt = 0u,
            text: String? = null,
        ) = KeyboardEvent(Type.Down, id, button, mask, text = text)

        fun up(
            id: UInt,
            button: UInt = 0u,
            mask: UInt = 0u,
            text: String? = null,
        ) = KeyboardEvent(Type.Up, id, button, mask, text = text)

        fun repeat(
            id: UInt,
            button: UInt = 0u,
            mask: UInt = 0u,
            count: Short = 0,
            text: String? = null,
        ) = KeyboardEvent(Type.Repeat, id, button, mask, count, text = text)
>>>>>>> trae/agent-0UI0AO
    }
}