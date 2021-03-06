import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO


var isVisible by mutableStateOf(true)
var graphicBitmap by mutableStateOf<GraphicBitmap?>(null)
var lastSaveFolder: String? = null

@ExperimentalComposeUiApi
@Composable
@Preview
fun App(applicationScope: ApplicationScope, windowScope: FrameWindowScope) {
    MaterialTheme {
        var delay by remember { mutableStateOf(0F) }
        var hideChecked by remember { mutableStateOf(false) }
        val coroutineContext = rememberCoroutineScope()

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
                            graphicBitmap = GraphicBitmap(img, img.createGraphics())
                        }
                    }
                }) {
                    Text("?????????????? ????????????????")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Slider(delay, { delay = it }, modifier = Modifier.width(100.dp))
                    Text("????????????????: ${"%.1f".format(delay * 5)}??")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hideChecked, { hideChecked = it })
                    Text("???????????????? ???? ??????????????????")
                }
                Spacer(modifier = Modifier.width(5.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (graphicBitmap != null) ImagePalette()
        }
        Toolbars(applicationScope, windowScope)
    }
}

@Composable
fun ImagePalette() {
    var brushColorFloat by remember { mutableStateOf(0F) }
    var brushColor by remember { mutableStateOf<java.awt.Color>(java.awt.Color.black) }
    var brushSizeFloat by remember { mutableStateOf(0.25F) }
    val magicNumbers = remember { MutablePair(1.0, 1.0) }
    var size : IntSize? = null;
    var imageRecompositionTrigger by remember { mutableStateOf(0) }
    val cuttingData = remember { CuttingData(mutableStateOf(false), null, null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            graphicBitmap = GraphicBitmap(
                graphicBitmap!!.backup,
                graphicBitmap!!.backup.createGraphics()
            )
            imageRecompositionTrigger++
        }) {
            Text("???????????????? ????????")
        }
        Spacer(modifier = Modifier.width(10.dp))
        cuttingData.isCutting.let {
            Button(
                colors = if (cuttingData.isCutting.value) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors(),
                onClick = {
                    cuttingData.isCutting.value = true
                }) {
                Text("????????????????")
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Slider(brushColorFloat, { brushColorFloat = it }, modifier = Modifier.width(200.dp))
        Slider(
            brushSizeFloat,
            { brushSizeFloat = if (it > 0.25) it else 0.25F },
            modifier = Modifier.width(80.dp).rotate(90f).padding(0.dp, 20.dp)
        )
        brushColorFloat.let {
            brushColor = java.awt.Color((it * 16777215).toInt())
            graphicBitmap!!.graphics.color = brushColor
        }
        brushColor.let {
            Box(
                modifier = Modifier.padding(30.dp, 0.dp).size((50 * brushSizeFloat).dp).clip(CircleShape)
                    .background(Color(brushColor.red, brushColor.green, brushColor.blue))
            )
        }
    }
    Spacer(modifier = Modifier.height(15.dp))
    imageRecompositionTrigger.let {
        Image(
            bitmap = graphicBitmap!!.bitmap.toComposeImageBitmap(),
            contentDescription = "Image Canvas",
            modifier = Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        imageRecompositionTrigger++
                        if (graphicBitmap != null) {
                            if (cuttingData.isCutting.value) {
                                val localX = if(change.position.x > size!!.width)  size!!.width else change.position.x
                                val localY = if(change.position.y > size!!.height)  size!!.height else change.position.y
                                cuttingData.secondPoint!!.first =
                                    ((magicNumbers.first * localX.toDouble()) - cuttingData.firstPoint!!.first).toInt()
                                cuttingData.secondPoint!!.second =
                                    ((magicNumbers.second * localY.toDouble()) - cuttingData.firstPoint!!.second).toInt()
                                graphicBitmap!!.bitmap = copyImage(graphicBitmap!!.rectBackup!!)
                                graphicBitmap!!.graphics = graphicBitmap!!.bitmap.createGraphics()
                                graphicBitmap!!.graphics.color = brushColor
                                graphicBitmap!!.graphics.stroke = BasicStroke(10.0F)
                                graphicBitmap!!.graphics.drawRect(
                                    cuttingData.firstPoint!!.first,
                                    cuttingData.firstPoint!!.second,
                                    cuttingData.secondPoint!!.first,
                                    cuttingData.secondPoint!!.second
                                )
                            } else {
                                graphicBitmap!!.graphics.fillOval(
                                    (magicNumbers.first * change.position.x).toInt(),
                                    (magicNumbers.second * change.position.y).toInt(),
                                    (brushSizeFloat * 100).toInt(),
                                    (brushSizeFloat * 100).toInt()
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        if (cuttingData.isCutting.value) {
                            imageRecompositionTrigger++
                            graphicBitmap!!.bitmap = graphicBitmap!!.rectBackup!!.getSubimage(
                                cuttingData.firstPoint!!.first,
                                cuttingData.firstPoint!!.second,
                                cuttingData.secondPoint!!.first - cuttingData.firstPoint!!.first,
                                cuttingData.secondPoint!!.second - cuttingData.firstPoint!!.second
                            )
                            graphicBitmap!!.backup = copyImage(graphicBitmap!!.bitmap)
                            graphicBitmap!!.graphics = graphicBitmap!!.bitmap.createGraphics()
                            cuttingData.isCutting.value = false
                        }
                    }
                )
            }.onGloballyPositioned {
                size = it.size
                magicNumbers.first = graphicBitmap!!.bitmap.width / it.size.width.toDouble()
                magicNumbers.second = graphicBitmap!!.bitmap.height / it.size.height.toDouble()
            }.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (cuttingData.isCutting.value) {
                        graphicBitmap!!.rectBackup = copyImage(graphicBitmap!!.bitmap)
                        cuttingData.firstPoint = MutablePair(
                            (magicNumbers.first * it.x).toInt(),
                            (magicNumbers.second * it.y).toInt()
                        )
                        cuttingData.secondPoint = MutablePair(0, 0)
                    } else {
                        imageRecompositionTrigger++
                        graphicBitmap!!.graphics.fillOval(
                            (magicNumbers.first * it.x).toInt(),
                            (magicNumbers.second * it.y).toInt(),
                            (brushSizeFloat * 100).toInt(),
                            (brushSizeFloat * 100).toInt()
                        )
                        graphicBitmap!!.graphics.setPaintMode()
                    }
                })
            })
    }
}

@ExperimentalComposeUiApi
@Composable
fun Toolbars(applicationScope: ApplicationScope, windowScope: FrameWindowScope) {
    var isOpenDialog by remember { mutableStateOf(0) }
    when (isOpenDialog) {
        1 -> {
            windowScope.FileDialog("???????????????? ???????? ?????? ????????????????", true) {
                isOpenDialog = 0
                if (it != null) {
                    try {
                        val img = ImageIO.read(it.toFile())
                        graphicBitmap = GraphicBitmap(img, img.createGraphics())
                        windowScope.window.title = it.fileName.toString()
                    } catch (_: IOException) {
                    }
                }
            }
        }
        2 -> {
            if (graphicBitmap == null) {
                isOpenDialog = 4
            } else {
                windowScope.FileDialog("???????????????? ???????? ?????? ????????????????????", false) {
                    isOpenDialog = 0
                    if (it != null) {
                        try {
                            ImageIO.write(graphicBitmap!!.bitmap, "png", it.toFile())
                            File("${System.getProperty("user.dir")}/slimshot.ini").writeText(it.parent.toString())
                            lastSaveFolder = it.parent.toString()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        3 -> {
            if (graphicBitmap == null) {
                isOpenDialog = 4
            } else {
                try {
                    var counter = 1
                    val homeFolder = System.getProperty("user.home")
                    while (File("${homeFolder}/SlimShotFastSave-${counter}.png").exists()) counter++
                    ImageIO.write(graphicBitmap!!.bitmap, "png", File("${homeFolder}/SlimShotFastSave-${counter}.png"))
                } catch (e: Exception) {
                }
            }
        }
        4 -> {
            Dialog(
                onCloseRequest = { isOpenDialog = 0 },
                state = rememberDialogState(position = WindowPosition(Alignment.Center))
            ) {
                Text("???????? ?????? ?????? ???????????? ??????????????????!")
            };
        }
    }
    windowScope.MenuBar {
        Menu(text = "????????") {
            Item("??????????????", shortcut = KeyShortcut(Key.O, true)) { isOpenDialog = 1 }
            Item("??????????????????", shortcut = KeyShortcut(Key.S, true)) { isOpenDialog = 2 }
            Item("?????????????? ????????????????????", shortcut = KeyShortcut(Key.F, true)) { isOpenDialog = 3 }
            Item("??????????") { applicationScope.exitApplication() }
        }
    }
}

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
    var bitmap: BufferedImage,
    var graphics: Graphics2D,
    var backup: BufferedImage = copyImage(bitmap),
    var rectBackup: BufferedImage? = null,
)

data class CuttingData(
    var isCutting: MutableState<Boolean>,
    var firstPoint: MutablePair<Int, Int>?,
    var secondPoint: MutablePair<Int, Int>?
)

data class MutablePair<T, _T>(
    var first: T,
    var second: _T
)

@Composable
fun FrameWindowScope.FileDialog(
    title: String,
    isLoad: Boolean,
    onResult: (result: Path?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(window, "???????????????? ????????", if (isLoad) LOAD else SAVE) {
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
            if (lastSaveFolder != null) {
                this.directory = lastSaveFolder
            }
            this.title = title
        }
    },
    dispose = FileDialog::dispose
)

@ExperimentalComposeUiApi
fun main() {
    try {
        lastSaveFolder = File("${System.getProperty("user.dir")}/slimshot.ini").readText()
    } catch (e: Exception) {

    }

    application {
        Window(visible = isVisible, onCloseRequest = ::exitApplication, title = "SlimShot") {
            App(this@application, this)
        }
    }
}
