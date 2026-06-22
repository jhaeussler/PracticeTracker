package org.jhaeussler.practicetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun ScrollableTable(
    headers: List<String>,
    data: List<Pair<List<String>, (() -> Unit)?>>,
    cellWith: Int,
    rowHeight: Int,
    visibleRowCount: Int = 3,
    modifier: Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .border(
                border = BorderStroke(width = 3.dp, color = Color.Black),
                shape = RoundedCornerShape(25.dp)
            )
    ) {
        TableHeader(
            scrollState = scrollState,
            headers = headers,
            width = cellWith,
            modifier = Modifier.height(70.dp).fillMaxWidth()
        )
        TableContent(
            scrollState = scrollState,
            data = data,
            width = cellWith,
            rowHeight = rowHeight,
            visibleRowCount = visibleRowCount,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TableHeader(
    modifier: Modifier,
    scrollState: ScrollState,
    width: Int,
    headers: List<String>
) {
    Row(
        horizontalArrangement =
        Arrangement.spacedBy(10.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .horizontalScroll(scrollState)
            .background(Color(0.05f, 0.2f, 0.2f, 0.9f))
            .padding(10.dp)
    ) {
        headers.forEach { header ->
            Text(
                text = header,
                modifier = Modifier
                    .width(width.dp)
                    .padding(8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 23.sp
            )
        }
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TableContent(
    modifier: Modifier,
    scrollState: ScrollState,
    width: Int,
    rowHeight: Int,
    visibleRowCount: Int,
    data: List<Pair<List<String>, (() -> Unit)?>>
) {
    LazyColumn(
        modifier = modifier.height(
            if(data.count() < visibleRowCount)
                (data.count() * rowHeight).dp
            else
                (visibleRowCount * rowHeight).dp
        )
    ) {
        items(data) { row ->
            Column (
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0.1f, 0.3f, 0.4f, 0.9f))
            ) {
                Box(
                    modifier =
                    if(row.second != null)
                        Modifier.clickable { (row.second ?: {}) () }
                    else
                        Modifier
                ) {
                    Row(
                        horizontalArrangement =
                        Arrangement.spacedBy(10.dp, alignment = Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((rowHeight -1).dp)
                            .horizontalScroll(scrollState)
                            .padding(vertical = 5.dp)
                    ) {
                        row.first.forEach { cell ->
                            Text(
                                text = cell,
                                textAlign = TextAlign.Center,
                                fontSize = 21.sp,
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(width.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
                if(row != data.last()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.Black,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}