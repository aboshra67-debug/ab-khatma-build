package com.ab.khatma.secure

import com.ab.khatma.guide.P85Guide.target
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
@DrawableRes private fun p16Image(index:Int):Int=when(index) {
    0 -> com.ab.khatma.R.drawable.home_card_mushaf
    1 -> com.ab.khatma.R.drawable.home_card_khatmas
    2 -> com.ab.khatma.R.drawable.home_card_join
    3 -> com.ab.khatma.R.drawable.home_card_prayer
    4 -> com.ab.khatma.R.drawable.home_card_profile
    5 -> com.ab.khatma.R.drawable.home_card_adhkar
    6 -> com.ab.khatma.R.drawable.home_daily_quran
    else -> com.ab.khatma.R.drawable.home_hero_masjid
}

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
        shape=RoundedCornerShape(25.dp),
        elevation=CardDefaults.cardElevation(defaultElevation=6.dp),
        colors=CardDefaults.cardColors(containerColor=emerald)) {
        Box(Modifier.fillMaxSize()
            .background(Brush.horizontalGradient(listOf(emerald,dark)))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.43f)
                .clip(RoundedCornerShape(topStart=24.dp,bottomStart=24.dp))) {
                Image(painterResource(p16Image(7)), contentDescription=null,
                    contentScale=ContentScale.Fit, modifier=Modifier.fillMaxSize().padding(5.dp))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.70f to Color.Transparent,
                    1f to emerald)))
            }
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
    Card(onClick=onRead,modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Color(0xFFD9E7DE)),
        elevation=CardDefaults.cardElevation(defaultElevation=4.dp,pressedElevation=1.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White)) {
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
            Image(painterResource(p16Image(6)),contentDescription=null,
                contentScale=ContentScale.Fit,
                modifier=Modifier.size(64.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFFFFAF0)).padding(3.dp))
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
    Card(onClick=onClick,enabled=enabled,
        modifier=modifier.height(height),shape=RoundedCornerShape(23.dp),
        border=BorderStroke(1.dp,rim),
        elevation=CardDefaults.cardElevation(defaultElevation=7.dp,
            pressedElevation=2.dp, disabledElevation=1.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White,
            disabledContainerColor=Color(0xFFFCFAF4))) {
        Row(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White,Color(0xFFFFF9EC))))
            .padding(horizontal=7.dp, vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Image(painterResource(p16Image(art)),contentDescription=null,
                contentScale=ContentScale.Fit,
                modifier=Modifier.size(height*0.69f).clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFFFFFAF1)).padding(3.dp))
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
fun KhatmaVisualHome(userName:String,groupCount:Int,groupsLoaded:Boolean, groupsLoadFailed:Boolean=false,
    onOpenMushaf:()->Unit,onOpenKhatmas:()->Unit,
    onJoinGroup:()->Unit,onOpenProfile:()->Unit,onOpenPrayer:()->Unit,
    onOpenAdhkar:()->Unit, onOpenIbtehal:()->Unit = {}, showGenericWard:Boolean=true, dailyReaderWards: @Composable () -> Unit = {}) {
    val width=LocalConfiguration.current.screenWidthDp.toFloat()
    val scale=(width/390f).coerceIn(0.82f,1.13f)
    val gap=(12f*scale).dp
    val cardHeight=(112f*scale).dp
    // Android locales are RTL by default. Fix illustration on the LEFT and
    // section text on the RIGHT to match the approved reference, for every device.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(gap)) {
        VisualHeader(onOpenProfile,(61f*scale).dp)
        VisualGreeting(userName,(136f*scale).dp)
        // Reader-specific server assignments replace the generic Quran shortcut.
        // The existing Quran button remains among the main sections.
        if (showGenericWard) VisualWard((86f*scale).dp,onOpenMushaf)
        dailyReaderWards()
        VisualHeading()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
            VisualButton("الختمات",
                if(groupsLoaded) "مجموعاتي: $groupCount" else if(groupsLoadFailed) "تعذر التحميل · تحديث" else "جاري التحميل",
                1,cardHeight,onClick=onOpenKhatmas,modifier=Modifier.weight(1f).target("home.khatmas"))
            VisualButton("المصحف","القراءة والتدبر",
                0,cardHeight,onClick=onOpenMushaf,modifier=Modifier.weight(1f).target("home.mushaf"))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
            VisualButton("الصلاة والأذان","المواقيت والتنبيهات",3,cardHeight,
                onClick=onOpenPrayer,modifier=Modifier.weight(1f).target("home.prayer"))
            VisualButton("ملتقى الختمات","قراء ومجموعات",2,cardHeight,
                onClick=onJoinGroup,modifier=Modifier.weight(1f).target("home.meetup"))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
            VisualButton("الأدعية والأذكار","حصن المسلم • أذكار يومية",5,cardHeight,
                onClick=onOpenAdhkar,modifier=Modifier.weight(1f).target("home.adhkar"))
            VisualButton("حسابي","الاسم والإعدادات",4,cardHeight,
                onClick=onOpenProfile,modifier=Modifier.weight(1f).target("home.profile"))
        }
        OutlinedButton(onClick=onOpenIbtehal,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) { Text("♫ الابتهالات والتواشيح",color=dark) }
        com.ab.khatma.guide.P85Guide.HelpButton(modifier=Modifier.fillMaxWidth())
    }
    }
}
