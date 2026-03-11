# Gutenberg Library Prototype

| Part | Table of Contents |
| -------- | ------- |
| I. | [Project Gutenberg Website](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/README.md#project-gutenberg-website) |
| II. | [Gutendex API](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/README.md#gutendex-api) |
| III. | [Gutenberg Library App](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/README.md#gutenberg-library-app) |
| IV. | [Software Requirements](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/README.md#software-requirements-table) |
| V. | [Next Steps](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/README.md#next-steps-must-do) |
| VI. | [Last Steps](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/README.md#last-steps-optional) |



## Project Gutenberg Website
![Gutenberg's Most Popular](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/GutenbergMostPopular.png "Gutenberg's Most Popular")
![Frankenstein Text Options](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/FrankensteinOptions.png "Frankenstein Text Options")


## Gutendex API
![Gutendex API](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/GutendexApi.png "Gutendex API")


## Gutenberg Library App

I have been trying to figure out how to use the XML/RDF format instead of the "Read Now" HTML format I was originally planning on using.

![Frankenstein Read Now (HTML)](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/FrankensteinText.png "Frankenstein Read Now (HTML)")

I think the easiest way is to use the API site Guntendex to try and get this metadata. Right now, my code is using a WebViewFormat to display certain pages of the site, which feels like cheating for certain pages such as the About, Frequently Downloaded, master Categories, Reading Lists, and Search Options tabs, and for the provideded download options for a book. I will have a WebView for the About sub menu pages of the navigation menu: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate. 

I got really good feedback and resources to try incorporating Tailwind/React animations for the book opening and page turning animations. I also got good feedback to try adding a "My Library" database to sort Favorites and Downloads, and I think that incorporating this with update and delete functionality will represent a functional local database. I also got feedback to try using MongoDB, SQLite or another NoSQL local database with Room Persistance.

So I'm hoping to incorporate these excellent suggestions and create a "Shelf" database that allows you to view your Downloaded and Favorited books by definining a schema and contract, creating a database using SQL helper, adding, deleting and reading data from the database while simultaenously being able to update the database with a return value of update(). After I debug the database (SQLite3 shell tool is a potential option). This way the database using Room in the app to include dependencies to the build.gradle.kts with KSP or annotationProcessor, but NOT BOTH.

I am still troubleshooting the [API](https://gutendex.com/) configurment to display the books. Right now, I am successfully displaying book text but cannot get the actual book text downloaded.
![Gutenberg Library App](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/GutenbergLibrary1.png "Gutenberg Library App")
![Gutenberg Library App](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/GutenbergLibrary2.png "Gutenberg Library App")


### Software Requirements Table
--------------------

| ID | Software  Requirement |
| -------- | ------- |
| 1. | The software shall have a floating navigation bar menu |
| 1.1 | The floating navigation bar menu includes the About, Frequently Downloaded, master Categories, Reading Lists, and Search Options tabs |
| 2. | The About section of the navigation menu shall have a sub menu |
| 2.1 | The sub menu will have: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate |
| 3. | The software shall use a script that reads the Project Gutenberg website and mirrors back the books in the application |
| 3.1 | The book will be downloaded from the website and saved locally on the device |
| 4. | There shall be Advanced Search filtering|
| 4.1 | Advanced Search fields such as Author, Title, Subject, Subject Areas, Language, Datatype, and Filetype |
| 5. | The app shall reflect the website homepage where the Newest Releases will always appear on the top shelf |
| 5.1 | The second shelf will have the Most Popular books |
| 5.2 | The books are lined up on shelves that will scroll horizontally |



### Next Steps (MUST DO):
1. Download the book and save locally in a database on the device
   - The book files (Plain Text UTF-8) downloading and saving locally
     - The book is currently bulk-loading one single file, so I'm looking to separate by chapter or page number
2. Display the text file
   - Present the book in e-reader format with multiple views
     - The book is currently bulk-loading one single file, text display isn't uniform
![Gutenberg Library App](https://github.com/56006966/GutenbergLibrary/blob/master/prototype/GutenbergLibrary3.png "Gutenberg Library App")
3. The home screen will show three "Shelves" that reflect the website homepage:
   - Recent Releases
   - Most Popular
   - My Library (locally saved books) 
4. Create a floating navigation menu that, when clicked on, displays the About, Frequently Downloaded, master Categories, Reading Lists, and Search Options tabs.
   - The About section of the navigation menu has a sub menu with: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate.
   - Donate and PayPal buttons will launch a browser to these designated websites.
   - There will be a Quick Search bar at the top of the submenu and the Search Options page will give more advanced filtering options
      - This would be easiest as a webview to the website but I wanted to try to make my own search options page to search gutenberg.org
5. My Library (local database) with Favorites, Downloads, and ability to update and delete lists
6. React/Tailwind animations:
     - book opening / covers closing / flipping pages
     - scroll bar (ladder)
     - loading screens
     - carousel book spinner

  
### Last Steps (OPTIONAL):
7. Accessibility features:
     - text scalability
     - contrast picker
     - dark mode
     - text-to-audio
8. Themes
     - acheivements with rewardable icons, covers, colors, etc.
  
