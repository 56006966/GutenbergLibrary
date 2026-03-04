# Project Gutenberg Library

| Part | Table of Contents |
| -------- | ------- |
| I. | [Preliminary Design Review (PDR)](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#i-preliminary-design-review) |
| II. | [Final Design Review (FDR) - DRAFT](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#ii-final-design-review-fdr---draft) |
| III. | [Wireframe solution architecture diagrams - DRAFT](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#iii-wireframe-solution-architecture-diagrams---draft) |
| IV. | [User Stories](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#iv-user-stories) |
| V. | [Initial Use Cases](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#v-initial-use-cases) |
| VI. | [Use Case Diagrams](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#vi-use-case-diagrams) |
| VII. | [Software Requirements Table](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#vii-software-requirements-table) |
| VIII. | [ERD and UML Class Diagrams](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/README.md#viii-erd-and-uml-class-diagrams) |





## I. Preliminary Design Review
-------------------------

In this paper, I will be specifying the key parts that I am proposing for my project. With this project, I would be creating a mobile application for an interactive library interface by using the free website Project Gutenberg as a database. I specifically chose this website because they do not have a designated application for their website as it is accessible for mobile-device view and e-reader view with accompanying downloadable PDF files. This would be an example of a database GUI.

1. I will create script that reads the website and lists the books in the application. The book will be downloaded from the website and saved locally on the device.
2. The app will then present the book in an e-reader format, like Amazon Kindle.
3.The app will have multiple views for different devices including computers, tablets, and phones. I'd also like to incorporate accessibility features so that text size can change and read aloud the text, and at a minimum include a dark mode.
4. When you first launch the app, it will reflect the website homepage where the Newest Releases will always appear on the top shelf.
4.5 The second shelf will have the Most Popular books. The books lined up on shelves that will scroll horizontally. From there you can filter the shelves by genre or search for a book by name or author.
5. I would also like to have a floating navigation bar that, when clicked on, displays the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs.
6. The About section of the navigation menu has a sub menu with: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate. Donate and PayPal buttons will launch a browser to these designated websites.


## II. Final Design Review (FDR) - DRAFT
----------------------------------------------------


While researching using Android Studio for this project, I found some important warnings to consider, including:
>1. If the sdkVersion is 17 or later, you need to add @JavascriptInterface annotation to any method you want available in Javascript. If it's not public the method won't be accessible.
>2. DO NOT call loadUrl(), reload(), or similar methods from within shouldOverrideUrlLoading(), leading to inefficiency. It's more efficient to return FALSE to let the WebView continue loading the URL
>3. DO NOT USE JavascriptInterface()
>4. DO NOT let a user navigate in a WebView of HTML that is NOT YOUR OWN HTML

[Project Gutenberg](https://www.gutenberg.org/ebooks/offline_catalogs.html) also has some pretty specific policies against web scraping: 
>All Project Gutenberg metadata are available digitally in the XML/RDF format. This is updated daily (other than the legacy format mentioned below). Please use one of these files as input to a database or other tools you may be developing, instead of crawling or roboting the website.
>Note that the exact same metadata is available as a per-eBook .rdf file. These are found in the cache/epub (i.e., cache/generated) directory, accessible by mirroring or by the directory/folder listings above. The large XML/RDF file is simply a concatenation of all the per-eBook metadata.

With this information, I have been trying to figure out how to use the XML/RDF format instead of the "Read Now" HTML format I was originally planning on using. I think the easiest way is to use the API site Guntendex to try and get this metadata.

Right now, my code is using a WebViewFormat to display certain pages of the site, which feels like cheating for certain pages such as the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs, and for the provideded download options for a book.

I will have a WebView for the About sub menu pages of the navigation menu: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate. 

I am still troubleshooting the [API](https://gutendex.com/) configurment to display the books. Right now, I am successfully displaying book cover images but cannot get the actual book text downloaded.

I got really good feedback and resources to try incorporating Tailwind/React animations for the book opening and page turning animations. 

I also got good feedback to try adding a "My Library" database to sort Favorites and Downloads, and I think that incorporating this with update and delete functionality will represent a functional local database. 

I also got feedback to try using MongoDB, SQLite or another NoSQL local database with Room Persistance 

So I'm hoping to incorporate these excellent suggestions and create a "Shelf" database that allows you to view your Downloaded and Favorited books by definining a schema and contract, creating a database using SQL helper, adding, deleting and reading data from the database while simultaenously being able to update the database with a return value of update(). After I debug the database (SQLite3 shell tool is a potential option). This way the database using Room in the app to include dependencies to the build.gradle.kts with KSP or annotationProcessor, but NOT BOTH.

Next Steps (MUST DO):
1. Create API script that reads data from gutendex.com (source database) and displays books inside the app
2. Download the book and save locally in a database on the device
   - The book files (Plain Text UTF-8) downloading locally
   - The book is currently bulk-loading one single file, looking to separate by chapter or page number
3. Display the text file
   - Present the book in e-reader format with multiple views
4. The home screen will show "Shelves" that reflect the website homepage:
   - Recent Releases
   - Most Popular
   - My Library (locally saved books) 
4. Create a floating navigation menu that, when clicked on, displays the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs.
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
Last Steps (OPTIONAL):
7. Accessibility features:
     - text scalability
     - contrast picker
     - dark mode
     - text-to-audio
8. Themes
     - acheivements with rewardable icons, covers, colors, etc.



## III. Wireframe solution architecture diagrams - DRAFT
----------------------------------------------------

![Wireframe 1](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/wireframe1.drawio.png "Wireframe Diagram 1")
![Wireframe 2](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/wireframe2.drawio.png "Wireframe Diagram 2")
![Wireframe 3](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/wireframe3.drawio.png "Wireframe Diagram 3")
![Wireframe 4](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/wireframe4.drawio.png "Wireframe Diagram 4")
![Wireframe 5](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/usecasediagram1.png "Use Case Diagram 1")
![Wireframe 6](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/usecasediagram2.png "Use Case Diagram 2")




## IV. User Stories
-----------------

1. As a Developer, I want/need a floating navigation bar, so that when clicked on, it displays the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs   

2. As a Customer, I want/need dark mode and text scalability, so that it incorporates accessibility features so that text size can change, be read aloud, and at a minimum include a dark mode.               

3. As a Developer I want/need the app will have multiple views, so that the app will have multiple views for different devices including computers, tablets, and phones

4. As a Developer I want/need The About section of the navigation menu has a sub menu so that The About section of the navigation menu has a sub menu with: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate.     

5. As a Developer I need the app to present the book in an e-reader format, so that I will create script that reads the website and lists the books in the application. The book will be downloaded from the website and saved locally on the device. The app will then present the book in an e-reader format, like Amazon Kindle.





## V. Initial Use Cases
---------------------

|  1. Brief description (or title): |  Floating navigation bar |
| -------- | ------- |
| The [relevant] system: | Floating navigation bar menu |
| The actor(s) including those initiating or responding to events: | When clicked on, it displays the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs |
| Scenario usually will include the pre-existing conditions and (all) relevant action sequences: | The About section of the navigation menu has a sub menu with: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate. |
| Expected result: | A fully functional floating navigation bar that when clicked on, it displays the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs. |

| 2. Brief description (or title): | Script |
| -------- | ------- |
| The [relevant] system: | Mobile application API script |
| The actor(s) including those initiating or responding to events: | The script that reads the Project Gutenberg website and lists the books in the application. The book will be downloaded from the website and saved locally on the device. |
| Scenario usually will include the pre-existing conditions and (all) relevant action sequences: | The book will be downloaded from the website and saved locally on the device.|
| Expected result: | A full functional script that reads the Project Gutenberg website and lists the books in the application. The book will be downloaded from the website and saved locally on the device. |      

| 3. Brief description (or title): | Multiple views |
| -------- | ------- |
| The [relevant] system: | Multiple views for different devices |
| The actor(s) including those initiating or responding to events: | The app will have multiple views for different devices including computers, tablets, and phones. |
| Scenario usually will include the pre-existing conditions and (all) relevant action sequences: | The app will have multiple views for different devices including computers, tablets, and phones. |
| Expected result: | The book will be downloaded from the website and saved locally on the device. The app will then present the book in an e-reader format, like Amazon Kindle. The app will have multiple views for different devices including computers, tablets, and phones. |

| 4. Brief description (or title): | Scrolling shelves |
| -------- | ------- |
| The [relevant] system: | Horizontal scrolling shelves filtered by genre |
| The actor(s) including those initiating or responding to events: | The books are lined up on shelves that will scroll horizontally. |
| Scenario usually will include the pre-existing conditions and (all) relevant action sequences: | The books are lined up on shelves that will scroll horizontally. |
| Expected result: | When you first launch the app, it will reflect the website homepage where the Newest Releases will always appear on the top shelf. The second shelf will have the Most Popular books. The books are lined up on shelves that will scroll horizontally. |            

| 5. Brief description (or title): | Advanced Search filtering |
| -------- | ------- |
| The [relevant] system: | Advanced Search filtering |
| The actor(s) including those initiating or responding to events: | Advanced Search filtering for fields such as Author, Title, Subject, Subject Areas, Language, Datatype, and Filetype. |
| Scenario usually will include the pre-existing conditions and (all) relevant action sequences: | Advanced Search filtering for fields such as Author, Title, Subject, Subject Areas, Language, Datatype, and Filetype. |
| Expected result: | From there you can filter the shelves by genre or search for a book by name or author. |





## VI. Use Case Diagrams
--------------------

![Read or Download Book Use Case Diagram](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/UseCaseDiagram.drawio.png "Read or Download Book Use Case Diagram")




## VII. Software Requirements Table
--------------------

| ID | Software  Requirement |
| -------- | ------- |
| 1 | The software shall have a floating navigation bar menu that can be clicked on to display the About, Frequently Downloaded, Main Categories, Reading Lists, and Search Options tabs |
| 2 | The About section of the navigation menu shall have a sub menu with: Contact Us, History & Philosophy, Kindles & eReaders, Help Pages, Offline Catalogs, and Donate |
| 3 | The software shall use a script that reads the Project Gutenberg website and mirrors back the books in the application. The book will be downloaded from the website and saved locally on the device |
| 4 | There shall be Advanced Search filtering for fields such as Author, Title, Subject, Subject Areas, Language, Datatype, and Filetype |
| 5 | The app shall reflect the website homepage where the Newest Releases will always appear on the top shelf. The second shelf will have the Most Popular books. The books are lined up on shelves that will scroll horizontally |



## VIII. ERD and UML Class Diagrams
--------------------
![ERD](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/Erd.png "ERD")
![UML Class Diagram](https://github.com/56006966/ProjectGutenbergLibrary/blob/main/Uml.png "UML Diagram")
