package com.ab.khatma.secure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Visual-only P14 changes; original session and group permissions are unchanged. */
private val emerald = Color(0xFF064D39)
private val dark = Color(0xFF073B2D)
private val gold = Color(0xFFC3A153)
private val rim = Color(0xFFE5DDCC)

@Composable
private fun VisualHeader(onAccount: () -> Unit, height: Dp) {
    Row(Modifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Surface(shape = CircleShape, color = Color.White,
            border = BorderStroke(1.dp,rim)) {
            Box(Modifier.size(42.dp),contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Notifications,null,tint=dark)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("خَتْمَة",color=dark,style=MaterialTheme.typography.headlineMedium,
                fontWeight=FontWeight.Bold)
            Image(painterResource(com.ab.khatma.R.drawable.ab_khatma_mark),
                "شعار ختمة",modifier=Modifier.size(51.dp),contentScale=ContentScale.Fit)
        }
        OutlinedIconButton(onClick=onAccount,modifier=Modifier.size(42.dp),
            border=BorderStroke(1.dp,rim)) {
            Icon(Icons.Default.Settings,"الإعدادات",tint=dark)
        }
    }
}

@Composable
private fun VisualGreeting(name:String,height:Dp) {
    Card(modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=emerald)) {
        Box(Modifier.fillMaxSize()
            .background(Brush.horizontalGradient(listOf(emerald,dark)))) {
            KhatmaArt(7,Modifier.fillMaxHeight().fillMaxWidth(0.39f)
                .clip(RoundedCornerShape(topStart=24.dp,bottomStart=24.dp)))
            Column(Modifier.align(Alignment.CenterEnd)
                .fillMaxWidth(0.68f).padding(end=14.dp),
                horizontalAlignment=Alignment.End,
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("السلام عليكم ورحمة الله",
                    color=Color(0xFFFFF0CC),style=MaterialTheme.typography.bodyMedium,
                    maxLines=1)
                Text(name.ifBlank { "أهلًا بك" },color=Color.White,
                    fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("يوم جديد وورد جديد من القرآن الكريم",
                    color=Color(0xFFE1EFE8),style=MaterialTheme.typography.bodySmall,
                    textAlign=TextAlign.End,maxLines=2)
            }
        }
    }
}

@Composable
private fun VisualWard(height:Dp,onRead:()->Unit) {
    OutlinedCard(onClick=onRead,modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Color(0xFFD9E7DE)),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.White)) {
        Row(Modifier.fillMaxSize().padding(horizontal=10.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Button(onClick=onRead,
                colors=ButtonDefaults.buttonColors(containerColor=emerald),
                shape=RoundedCornerShape(24.dp),
                modifier=Modifier.height(46.dp),
                contentPadding=PaddingValues(horizontal=13.dp)) {
                Text("‹",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(5.dp))
                Text("اقرأ",fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.MenuBook,null,modifier=Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                Text("وردك اليوم",color=dark,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleMedium,maxLines=1)
                Text("افتح المصحف وتابع قراءتك",color=Color(0xFF627367),
                    style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.End,
                    maxLines=2)
            }
            KhatmaArt(6,Modifier.size(64.dp))
        }
    }
}

@Composable
private fun VisualHeading() {
    Row(Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        HorizontalDivider(Modifier.weight(1f),color=gold)
        Text("✧",color=gold)
        Text("الأقسام الرئيسية",color=dark,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleLarge,maxLines=1)
        Text("✧",color=gold)
        HorizontalDivider(Modifier.weight(1f),color=gold)
    }
}

@Composable
private fun VisualButton(title:String,subtitle:String,art:Int,
    height:Dp,enabled:Boolean=true,onClick:()->Unit,modifier:Modifier=Modifier) {
    OutlinedCard(onClick=onClick,enabled=enabled,
        modifier=modifier.height(height),shape=RoundedCornerShape(21.dp),
        border=BorderStroke(1.dp,rim),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.White,
            disabledContainerColor=Color(0xFFFCFAF4))) {
        Row(Modifier.fillMaxSize().padding(4.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            KhatmaArt(art,Modifier.size(height*0.77f))
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                Text(title,color=dark,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.bodyMedium,maxLines=2,
                    overflow=TextOverflow.Ellipsis,textAlign=TextAlign.End)
                Spacer(Modifier.height(4.dp))
                Text(subtitle,color=Color(0xFF687970),
                    style=MaterialTheme.typography.labelSmall,maxLines=2,
                    overflow=TextOverflow.Ellipsis,textAlign=TextAlign.End)
            }
        }
    }
}

@Composable
fun KhatmaVisualHome(userName:String,groupCount:Int,groupsLoaded:Boolean,
    onOpenMushaf:()->Unit,onOpenKhatmas:()->Unit,
    onJoinGroup:()->Unit,onOpenProfile:()->Unit) {
    val width=LocalConfiguration.current.screenWidthDp.toFloat()
    val scale=(width/390f).coerceIn(0.82f,1.13f)
    val gap=(8f*scale).dp
    val cardHeight=(100f*scale).dp
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(gap)) {
        VisualHeader(onOpenProfile,(61f*scale).dp)
        VisualGreeting(userName,(136f*scale).dp)
        VisualWard((81f*scale).dp,onOpenMushaf)
        VisualHeading()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
            VisualButton("الختمات",
                if(groupsLoaded) "مجموعاتي: $groupCount" else "جاري التحميل",
                1,cardHeight,onClick=onOpenKhatmas,modifier=Modifier.weight(1f))
            VisualButton("المصحف","القراءة والتدبر",
                0,cardHeight,onClick=onOpenMushaf,modifier=Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
            VisualButton("الصلاة والأذان","قريبًا",3,cardHeight,enabled=false,
                onClick={},modifier=Modifier.weight(1f))
            VisualButton("الانضمام","برمز الدعوة",2,cardHeight,
                onClick=onJoinGroup,modifier=Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
            VisualButton("الأدعية والأذكار","قريبًا",5,cardHeight,enabled=false,
                onClick={},modifier=Modifier.weight(1f))
            VisualButton("حسابي","الاسم والإعدادات",4,cardHeight,
                onClick=onOpenProfile,modifier=Modifier.weight(1f))
        }
    }
}
