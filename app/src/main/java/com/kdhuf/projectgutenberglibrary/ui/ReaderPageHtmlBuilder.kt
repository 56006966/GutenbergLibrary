package com.kdhuf.projectgutenberglibrary.ui

internal object ReaderPageHtmlBuilder {
    fun build(pageBody: String, inkModeEnabled: Boolean): String {
        val background = if (inkModeEnabled) "#111111" else "#faf7f0"
        val textColor = if (inkModeEnabled) "#d8d1bf" else "#111111"
        val figureSurface = if (inkModeEnabled) "#171717" else "#f1ead8"
        val figureBorder = if (inkModeEnabled) "#353535" else "#d7ccb2"
        val figureShadow = if (inkModeEnabled) "rgba(0,0,0,0.28)" else "rgba(58,40,13,0.10)"
        return """
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                    html {
                        background: $background;
                    }
                    body {
                        font-family: serif;
                        line-height: 1.6;
                        padding: 28px 24px 32px 24px;
                        margin: 0 auto;
                        min-height: 100vh;
                        box-sizing: border-box;
                        color: $textColor;
                        background: $background;
                        max-width: 980px;
                        -webkit-user-select: none;
                        user-select: none;
                        -webkit-touch-callout: none;
                    }
                    .reader-image {
                        max-width: 100%;
                        height: auto;
                    }
                    .reader-image--content {
                        display: block;
                        margin: 0.5rem auto 0.85rem auto;
                        border-radius: 14px;
                    }
                    .reader-image--decorative {
                        display: inline-block;
                        vertical-align: text-bottom;
                        margin: 0;
                    }
                    .reader-illustration {
                        margin: 0 0 1.35rem 0;
                        padding: 0.9rem;
                        border-radius: 20px;
                        background: $figureSurface;
                        border: 1px solid $figureBorder;
                        box-shadow: 0 14px 34px $figureShadow;
                        overflow: hidden;
                    }
                    .reader-illustration figure,
                    .reader-illustration div,
                    .reader-illustration p {
                        margin: 0;
                    }
                    .reader-illustration .reader-image--content {
                        float: none !important;
                        clear: both !important;
                    }
                    .reader-illustration--hero {
                        min-height: calc(100vh - 110px);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 1.2rem;
                        text-align: center;
                    }
                    .reader-illustration--hero .reader-image--content {
                        max-width: min(96%, 720px);
                        max-height: 72vh;
                        object-fit: contain;
                        margin-bottom: 0;
                    }
                    .reader-illustration--captioned {
                        padding: 1rem 1rem 0.85rem 1rem;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        text-align: center;
                    }
                    .reader-illustration--captioned .reader-image--content {
                        width: auto;
                        max-width: min(100%, 760px);
                        max-height: 55vh;
                        object-fit: contain;
                    }
                    .reader-illustration--captioned figcaption,
                    .reader-illustration--captioned small,
                    .reader-illustration--captioned em {
                        display: block;
                        margin-top: 0.75rem;
                        opacity: 0.82;
                    }
                    .reader-illustration--inline {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: flex-start;
                        gap: 0.85rem;
                    }
                    .reader-illustration--inline > * {
                        width: 100%;
                        max-width: 860px;
                    }
                    .reader-illustration--inline .reader-image--content {
                        width: auto;
                        max-width: min(100%, 760px);
                        max-height: 58vh;
                        object-fit: contain;
                        margin-left: auto;
                        margin-right: auto;
                    }
                    .reader-illustration--gallery {
                        display: grid;
                        gap: 0.85rem;
                        justify-items: center;
                    }
                    .reader-illustration--gallery .reader-image--content {
                        max-height: 40vh;
                        width: auto;
                        max-width: min(100%, 760px);
                        object-fit: contain;
                    }
                    img[align="left"],
                    img[align="right"],
                    img[style*="float:left"],
                    img[style*="float: left"],
                    img[style*="float:right"],
                    img[style*="float: right"],
                    img[class*="drop"],
                    img[class*="initial"],
                    img[src*="drop"],
                    .dropcap,
                    .dropcap img,
                    .figleft,
                    .figleft img,
                    .figright,
                    .figright img,
                    .initial,
                    .initial img {
                        float: none !important;
                        clear: none !important;
                        display: inline-block !important;
                        vertical-align: text-bottom !important;
                        margin: 0 0.12em 0 0 !important;
                        max-height: 1.7em !important;
                        max-width: none !important;
                        width: auto !important;
                    }
                    .decorative-initial-wrapper {
                        display: inline;
                        width: auto !important;
                    }
                    .decorative-initial-wrapper img {
                        margin-right: 0.12em !important;
                    }
                    p {
                        margin: 0 0 1em 0;
                    }
                    .tts-word--active {
                        color: #b53a24 !important;
                        background: rgba(225, 152, 107, 0.28);
                        border-radius: 0.18em;
                        box-shadow: 0 0 0 0.08em rgba(225, 152, 107, 0.18);
                    }
                    .tts-word {
                        cursor: pointer;
                        -webkit-user-select: none;
                        user-select: none;
                        -webkit-touch-callout: none;
                    }
                    @media (max-width: 720px) {
                        body {
                            padding: 20px 16px 24px 16px;
                            max-width: none;
                        }
                        .reader-illustration {
                            border-radius: 16px;
                            padding: 0.7rem;
                        }
                        .reader-illustration--hero {
                            min-height: auto;
                            padding: 0.85rem;
                        }
                        .reader-illustration--hero .reader-image--content {
                            max-height: 56vh;
                        }
                        .reader-illustration--inline .reader-image--content,
                        .reader-illustration--captioned .reader-image--content,
                        .reader-illustration--gallery .reader-image--content {
                            max-height: 48vh;
                        }
                    }
                    a {
                        color: inherit;
                        text-decoration: none;
                        pointer-events: none;
                    }
                </style>
            </head>
            <body>$pageBody</body>
            <script>
                (function() {
                    if (window.readerTts) {
                        return;
                    }

                    function wordIndexFromPoint(clientX, clientY) {
                        var directElements = document.elementsFromPoint
                            ? document.elementsFromPoint(clientX, clientY)
                            : [document.elementFromPoint(clientX, clientY)];

                        for (var i = 0; i < directElements.length; i += 1) {
                            var candidate = directElements[i];
                            if (!candidate) {
                                continue;
                            }
                            var directWord = candidate.closest ? candidate.closest('[data-tts-word-index]') : null;
                            if (directWord) {
                                return Number(directWord.getAttribute('data-tts-word-index'));
                            }
                        }

                        var words = document.querySelectorAll('[data-tts-word-index]');
                        var bestIndex = -1;
                        var bestDistance = Number.POSITIVE_INFINITY;
                        for (var wordIdx = 0; wordIdx < words.length; wordIdx += 1) {
                            var word = words[wordIdx];
                            var rect = word.getBoundingClientRect();
                            if (rect.width <= 0 || rect.height <= 0) {
                                continue;
                            }
                            var dx = 0;
                            if (clientX < rect.left) {
                                dx = rect.left - clientX;
                            } else if (clientX > rect.right) {
                                dx = clientX - rect.right;
                            }
                            var dy = 0;
                            if (clientY < rect.top) {
                                dy = rect.top - clientY;
                            } else if (clientY > rect.bottom) {
                                dy = clientY - rect.bottom;
                            }
                            var distance = (dx * dx) + (dy * dy);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                bestIndex = Number(word.getAttribute('data-tts-word-index'));
                            }
                        }
                        return bestIndex;
                    }

                    function wrapTextNode(node, state) {
                        var text = node.nodeValue;
                        if (!text || !text.trim()) {
                            return;
                        }

                        var fragment = document.createDocumentFragment();
                        var regex = /\\S+/g;
                        var lastIndex = 0;
                        var match;

                        while ((match = regex.exec(text)) !== null) {
                            if (match.index > lastIndex) {
                                fragment.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
                            }

                            var wordIndex = state.wordCount;
                            var span = document.createElement('span');
                            span.className = 'tts-word';
                            span.setAttribute('data-tts-word-index', String(wordIndex));
                            span.textContent = match[0];
                            span.addEventListener('contextmenu', function(event) {
                                event.preventDefault();
                                event.stopPropagation();
                            });
                            span.addEventListener('pointerdown', function(event) {
                                if (!window.readerTts) {
                                    return;
                                }
                                if (window.getSelection && typeof window.getSelection === 'function') {
                                    var selection = window.getSelection();
                                    if (selection && typeof selection.removeAllRanges === 'function') {
                                        selection.removeAllRanges();
                                    }
                                }
                                window.readerTts.dragging = true;
                                window.readerTts.setActiveWord(wordIndex);
                                event.preventDefault();
                                event.stopPropagation();
                            });
                            span.addEventListener('click', function(event) {
                                event.stopPropagation();
                                if (window.AndroidReader && typeof window.AndroidReader.onWordTapped === 'function') {
                                    window.AndroidReader.onWordTapped(wordIndex);
                                }
                            });
                            fragment.appendChild(span);
                            state.wordCount += 1;
                            lastIndex = match.index + match[0].length;
                        }

                        if (lastIndex < text.length) {
                            fragment.appendChild(document.createTextNode(text.slice(lastIndex)));
                        }

                        node.parentNode.replaceChild(fragment, node);
                    }

                    function walk(node, state) {
                        if (!node) {
                            return;
                        }
                        if (node.nodeType === Node.TEXT_NODE) {
                            wrapTextNode(node, state);
                            return;
                        }
                        if (node.nodeType !== Node.ELEMENT_NODE) {
                            return;
                        }
                        if (node.tagName === 'SCRIPT' || node.tagName === 'STYLE') {
                            return;
                        }

                        var children = Array.prototype.slice.call(node.childNodes);
                        for (var i = 0; i < children.length; i += 1) {
                            walk(children[i], state);
                        }
                    }

                    window.readerTts = {
                        prepared: false,
                        wordCount: 0,
                        activeIndex: -1,
                        dragging: false,
                        prepare: function() {
                            if (this.prepared) {
                                return this.wordCount;
                            }
                            var state = { wordCount: 0 };
                            walk(document.body, state);
                            this.wordCount = state.wordCount;
                            this.prepared = true;
                            return this.wordCount;
                        },
                        clearActiveWord: function() {
                            if (this.activeIndex < 0) {
                                return;
                            }
                            var previous = document.querySelector('[data-tts-word-index="' + this.activeIndex + '"]');
                            if (previous) {
                                previous.classList.remove('tts-word--active');
                            }
                            this.activeIndex = -1;
                        },
                        setActiveWord: function(index) {
                            this.prepare();
                            this.clearActiveWord();
                            var next = document.querySelector('[data-tts-word-index="' + index + '"]');
                            if (!next) {
                                return;
                            }
                            next.classList.add('tts-word--active');
                            this.activeIndex = index;
                            next.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'auto' });
                        },
                        findWordIndexAtFraction: function(xFraction, yFraction) {
                            this.prepare();
                            var viewportWidth = Math.max(window.innerWidth || 0, 1);
                            var viewportHeight = Math.max(window.innerHeight || 0, 1);
                            var clientX = Math.max(0, Math.min(viewportWidth - 1, Math.round(viewportWidth * xFraction)));
                            var clientY = Math.max(0, Math.min(viewportHeight - 1, Math.round(viewportHeight * yFraction)));
                            return wordIndexFromPoint(clientX, clientY);
                        }
                    };

                    document.addEventListener('pointermove', function(event) {
                        if (!window.readerTts || !window.readerTts.dragging) {
                            return;
                        }
                        var draggedIndex = wordIndexFromPoint(event.clientX, event.clientY);
                        if (draggedIndex >= 0) {
                            window.readerTts.setActiveWord(draggedIndex);
                        }
                    }, { passive: true });

                    document.addEventListener('pointerup', function(event) {
                        if (!window.readerTts || !window.readerTts.dragging) {
                            return;
                        }
                        window.readerTts.dragging = false;
                        if (window.getSelection && typeof window.getSelection === 'function') {
                            var selection = window.getSelection();
                            if (selection && typeof selection.removeAllRanges === 'function') {
                                selection.removeAllRanges();
                            }
                        }
                        var releasedIndex = wordIndexFromPoint(event.clientX, event.clientY);
                        if (releasedIndex >= 0) {
                            window.readerTts.setActiveWord(releasedIndex);
                            if (window.AndroidReader && typeof window.AndroidReader.onWordTapped === 'function') {
                                window.AndroidReader.onWordTapped(releasedIndex);
                            }
                        } else if (window.readerTts.activeIndex >= 0 &&
                            window.AndroidReader && typeof window.AndroidReader.onWordTapped === 'function') {
                            window.AndroidReader.onWordTapped(window.readerTts.activeIndex);
                        }
                    }, { passive: true });

                    document.addEventListener('contextmenu', function(event) {
                        event.preventDefault();
                    });

                    window.readerTts.prepare();
                })();
            </script>
            </html>
        """.trimIndent()
    }
}
