package ru.drevoinfo.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArticleRecordTest {
    @Test
    fun parsesIdAndTitle() {
        val record = ArticleRecord.parse("17<drevo> АВВА <drevo>1<drevo> <drevo> Текст ")

        assertEquals(17L, record.id)
        assertEquals("АВВА", record.title)
    }

    @Test
    fun mainPageTitleIsStoredAsItIs() {
        val record = ArticleRecord.parse("4<drevo>ГЛАВНАЯ СТРАНИЦА<drevo>13<drevo> <drevo>Текст")

        assertEquals("ГЛАВНАЯ СТРАНИЦА", record.title)
    }

    @Test
    fun trimsOnlyControlCharactersAndSpacesLikeJavaTrim() {
        val record = ArticleRecord.parse("5<drevo> АВВА\t<drevo><drevo> <drevo>Текст")

        assertEquals(" АВВА", record.title)
    }

    @Test
    fun addsAllArticlesTopicWhenEveryTopicIsBelow13() {
        val record = ArticleRecord.parse("5<drevo>А<drevo> 1,12 <drevo> <drevo>Текст")

        assertEquals(listOf(1, 12, ALL_ARTICLES_TOPIC), record.topics)
    }

    @Test
    fun leavesOutAllArticlesTopicWhenAnyTopicIs13OrAbove() {
        val record = ArticleRecord.parse("5<drevo>А<drevo>1,13<drevo> <drevo>Текст")

        assertEquals(listOf(1, 13), record.topics)
    }

    @Test
    fun mainPageIsAlwaysInAllArticlesTopic() {
        val record = ArticleRecord.parse("4<drevo>ГЛАВНАЯ СТРАНИЦА<drevo>13<drevo> <drevo>Текст")

        assertEquals(listOf(13, ALL_ARTICLES_TOPIC), record.topics)
    }

    @Test
    fun articleWithoutTopicsIsInAllArticlesTopicOnly() {
        val record = ArticleRecord.parse("5<drevo>А<drevo> <drevo> <drevo>Текст")

        assertEquals(listOf(ALL_ARTICLES_TOPIC), record.topics)
    }

    @Test
    fun rejectsLineWithFewerThanFiveFields() {
        val error = assertThrows(IllegalArgumentException::class.java) { ArticleRecord.parse("5<drevo>А<drevo>1") }

        assertEquals("expected 5 <drevo>-separated fields, found 3", error.message)
    }

    @Test
    fun turnsTocLinksIntoAnchorTitlePairsAndMarksNestedEntries() {
        val toc =
            """ <a href="#t0"> Начало статьи </a> <div class="indent"> <a href="#t3"> Читателям </a> </div> """

        val record = ArticleRecord.parse("5<drevo>А<drevo>1<drevo>$toc<drevo> Тело статьи ")

        assertEquals("#t0| Начало статьи #_t3| Читателям  <drevo> Тело статьи", record.text)
    }
}
