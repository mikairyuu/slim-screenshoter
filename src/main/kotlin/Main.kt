// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path


suspend fun makeScreenshot(delay: Float): BufferedImage {
    delay((delay * 5 * 1000).toLong())
    return Robot().createScreenCapture(Rectangle(Toolkit.getDefaultToolkit().screenSize.size))
}

fun copyImage(source: BufferedImage): BufferedImage {
    val b = BufferedImage(source.width, source.height, source.type)
    val g = b.graphics
    g.drawImage(source, 0, 0, null)
    g.dispose()
    return b
}

data class GraphicBitmap(
    val bitmap: BufferedImage,
    val graphics: Graphics,
    val backup: BufferedImage = copyImage(bitmap)
)

data class MagicNumbers(
    val xMagic: Double,
    val yMagic: Double
)

var isVisible by mutableStateOf(true)

@Composable
@Preview
fun App(applicationScope: ApplicationScope, windowScope: FrameWindowScope) {
    MaterialTheme {
        var delay by remember { mutableStateOf(0F) }
        var hideChecked by remember { mutableStateOf(false) }
        val coroutineContext = rememberCoroutineScope()
        var imgToDisplay by remember { mutableStateOf<GraphicBitmap?>(null) }
        var brushColorFloat by remember { mutableStateOf(0F) }
        var brushColor by remember { mutableStateOf<java.awt.Color>(java.awt.Color.black) }
        var brushSizeFloat by remember { mutableStateOf(0.25F) }
        var magicNumbers = remember { MagicNumbers(1.0, 1.0) }
        var imageRecompositionTrigger by remember { mutableStateOf(0) }


        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    coroutineContext.launch {
                        withContext(Dispatchers.IO) {
                            if (hideChecked) {
                                isVisible = false
                                delay(10)
                            }
                            val img = makeScreenshot(delay)
                            if (hideChecked) isVisible = true
                            imgToDisplay = GraphicBitmap(img, img.graphics)
                        }
                    }
                }) {
                    Text("Создать скриншот")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Slider(delay, { delay = it }, modifier = Modifier.width(100.dp))
                    Text("Задержка: ${"%.1f".format(delay * 5)}с")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hideChecked, { hideChecked = it })
                    Text("Свернуть до скриншота")
                }
                Spacer(modifier = Modifier.width(20.dp))
                if (imgToDisplay != null) {
                    Slider(brushColorFloat, { brushColorFloat = it }, modifier = Modifier.width(200.dp))
                    Slider(
                        brushSizeFloat,
                        { brushSizeFloat = if (it > 0.25) it else 0.25F },
                        modifier = Modifier.width(80.dp).rotate(90f).padding(0.dp, 20.dp)
                    )
                    brushColorFloat.let {
                        brushColor = java.awt.Color((it * 16777215).toInt())
                    }
                    brushColor.let {
                        Box(
                            modifier = Modifier.padding(30.dp, 0.dp).size((50 * brushSizeFloat).dp).clip(CircleShape)
                                .background(Color(brushColor.red, brushColor.green, brushColor.blue))
                        )
                    }
                }
                Button(onClick = {
                    coroutineContext.launch {
                        withContext(Dispatchers.IO) {
                            if (hideChecked) {
                                isVisible = false
                                delay(10)
                            }
                            val img = makeScreenshot(delay)
                            if (hideChecked) isVisible = true
                            imgToDisplay = GraphicBitmap(img, img.graphics)
                        }
                    }
                }) {
                    Text("Очистить поле")
                }
            }
            Spacer(modifier = Modifier.height(15.dp))
            imageRecompositionTrigger.let {
                if (imgToDisplay != null) {
                    Image(
                        bitmap = imgToDisplay!!.bitmap.toComposeImageBitmap(),
                        contentDescription = "Image Canvas",
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                imgToDisplay!!.graphics.color = brushColor
                                imageRecompositionTrigger++
                                imgToDisplay!!.graphics.fillOval(
                                    (magicNumbers.xMagic * change.position.x).toInt(),
                                    (magicNumbers.yMagic * change.position.y).toInt(),
                                    (brushSizeFloat * 100).toInt(),
                                    (brushSizeFloat * 100).toInt()
                                )
                            }
                        }.onGloballyPositioned {
                            magicNumbers = MagicNumbers(
                                (imgToDisplay!!.bitmap.width / it.size.width.toDouble()),
                                (imgToDisplay!!.bitmap.height / it.size.height.toDouble())
                            )
                        })
                }
            }
        }
        Toolbars(applicationScope, windowScope)
    }
}

@Composable
fun Toolbars(applicationScope: ApplicationScope, windowScope: FrameWindowScope) {
    var isOpenDialog by remember { mutableStateOf(0) }
    if (isOpenDialog == 1) {
        windowScope.FileDialog("Выберите файл для открытия", true) {
            isOpenDialog = 0
        }
    } else if (isOpenDialog == 2) {
        windowScope.FileDialog("Выберите файл для сохранения", true) {
            isOpenDialog = 0
        }
    }
    windowScope.MenuBar {
        Menu(text = "Файл") {
            Item("Открыть") { isOpenDialog = 1 }
            Item("Сохранить") { isOpenDialog = 2 }
            Item("Выйти") { applicationScope.exitApplication() }
        }
    }
}

@Composable
fun FrameWindowScope.FileDialog(
    title: String,
    isLoad: Boolean,
    onResult: (result: Path?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(window, "Выберите файл", if (isLoad) LOAD else SAVE) {
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    if (file != null) {
                        onResult(File(directory).resolve(file).toPath())
                    } else {
                        onResult(null)
                    }
                }
            }
        }.apply {
            this.title = title
        }
    },
    dispose = FileDialog::dispose
)

fun main() = application {
    Window(visible = isVisible, onCloseRequest = ::exitApplication) {
        App(this@application, this)
    }
}
