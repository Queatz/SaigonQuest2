package quest.saigon.web

import quest.saigon.Language
import web.html.HTMLElement

private const val LEGAL_CONTACT_EMAIL = "jacobaferrero@gmail.com"

private data class PolicySection(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    val contactLabel: String? = null,
)

internal fun privacyScreen(router: HistoryRouter): HTMLElement = legalScreen(
    router = router,
    title = Town.t("privacyPolicy"),
    updated = if (Town.language == Language.VI) "Cập nhật lần cuối: 20 tháng 11 năm 2026" else "Last updated: 20 November 2026",
    sections = privacySections(),
)

internal fun csaeScreen(router: HistoryRouter): HTMLElement = legalScreen(
    router = router,
    title = Town.t("csaePolicy"),
    updated = if (Town.language == Language.VI) "Cập nhật lần cuối: 20 tháng 11 năm 2026" else "Last updated: 20 November 2026",
    sections = csaeSections(),
)

private fun legalScreen(
    router: HistoryRouter,
    title: String,
    updated: String,
    sections: List<PolicySection>,
): HTMLElement {
    val shell = el("section", "shell page-shell legal-shell")
    shell.appendChild(pageHead(router, title, showProfile = false))
    val body = el("div", "page-body legal-body")
    val article = el("article", "legal-article")
    article.appendChild(el("p", "legal-updated", text = updated))
    sections.forEach { section ->
        val content = el("section", "legal-section")
        content.appendChild(el("h2", text = section.title))
        section.paragraphs.forEach { paragraph ->
            val p = el("p")
            appendLinkedText(p, paragraph)
            content.appendChild(p)
        }
        if (section.bullets.isNotEmpty()) {
            val list = el("ul")
            section.bullets.forEach { bullet ->
                val item = el("li")
                appendLinkedText(item, bullet)
                list.appendChild(item)
            }
            content.appendChild(list)
        }
        section.contactLabel?.let { label ->
            val contact = el("p")
            contact.appendChild(el("span", text = label))
            val link = el("a", text = LEGAL_CONTACT_EMAIL)
            link.setAttribute("href", "mailto:$LEGAL_CONTACT_EMAIL")
            contact.appendChild(link)
            content.appendChild(contact)
        }
        article.appendChild(content)
    }
    body.appendChild(article)
    shell.appendChild(body)
    return shell
}

private fun privacySections(): List<PolicySection> = if (Town.language == Language.VI) {
    listOf(
        PolicySection(
            title = "Đơn vị phụ trách và phạm vi",
            paragraphs = listOf(
                "Chính sách này áp dụng cho Saigon.Town trên web và các dịch vụ liên quan. Saigon.Town quyết định mục đích và cách xử lý dữ liệu cá nhân để vận hành dịch vụ. Hãy liên hệ chúng tôi nếu bạn có câu hỏi hoặc yêu cầu về dữ liệu.",
            ),
            contactLabel = "Liên hệ về quyền riêng tư: ",
        ),
        PolicySection(
            title = "Dữ liệu chúng tôi xử lý",
            paragraphs = listOf("Tùy cách bạn dùng dịch vụ, dữ liệu có thể bao gồm:"),
            bullets = listOf(
                "Thông tin tài khoản và liên hệ: số điện thoại dùng Zalo, thông tin xác thực tài khoản, tên hiển thị, phần giới thiệu, ảnh hồ sơ và ngôn ngữ.",
                "Nội dung và trao đổi: bài đăng, ảnh, bình luận, tin nhắn, nội dung hồ sơ, lời nhắc và ảnh được dùng khi bạn chủ động tạo avatar bằng AI.",
                "Vị trí: địa điểm, tọa độ hoặc thời gian check-in mà bạn chọn chia sẻ, cùng dữ liệu cần thiết để tìm kiếm địa điểm và hiển thị bản đồ.",
                "Dữ liệu kỹ thuật: cookie hoặc bộ nhớ trình duyệt cần thiết cho phiên đăng nhập, bảo mật và tùy chọn ngôn ngữ; thông tin yêu cầu và thống kê lượt truy cập ở mức cần thiết để vận hành, bảo vệ và cải thiện dịch vụ.",
                "Thông báo đẩy: mã đăng ký thiết bị/trình duyệt và trạng thái gửi thông báo nếu bạn bật thông báo web hoặc ứng dụng.",
            ),
        ),
        PolicySection(
            title = "Mục đích và cơ sở xử lý",
            paragraphs = listOf(
                "Chúng tôi dùng dữ liệu để tạo và bảo vệ tài khoản; hiển thị hồ sơ, bài đăng, tin nhắn, hoạt động và check-in; cung cấp bản đồ, thông báo và tính năng bạn yêu cầu; hỗ trợ khách hàng; ngăn gian lận, lạm dụng và nguy cơ an toàn; tuân thủ nghĩa vụ pháp lý; và hiểu hiệu năng, mức sử dụng để duy trì, cải thiện dịch vụ.",
                "Tùy luật áp dụng và từng hoạt động, cơ sở xử lý có thể là thực hiện dịch vụ bạn yêu cầu, sự đồng ý của bạn, lợi ích hợp pháp như bảo mật và vận hành, hoặc nghĩa vụ pháp lý. Khi xử lý dựa trên sự đồng ý, bạn có thể rút lại; việc rút lại không làm mất hiệu lực của xử lý trước đó hoặc cơ sở xử lý khác theo luật.",
            ),
        ),
        PolicySection(
            title = "Nội dung hiển thị và chia sẻ dữ liệu",
            paragraphs = listOf(
                "Một số nội dung bạn đăng hoặc chủ động chia sẻ—như tên, ảnh, bài đăng, địa điểm và check-in—có thể được người dùng khác nhìn thấy theo cách tính năng hoạt động. Chỉ chia sẻ thông tin bạn muốn người nhận hoặc cộng đồng tương ứng nhìn thấy. Tin nhắn được cung cấp cho người tham gia cuộc trò chuyện; nhân sự được ủy quyền có thể truy cập khi cần để hỗ trợ, bảo vệ an toàn hoặc xử lý vi phạm.",
                "Chúng tôi chia sẻ lượng dữ liệu cần thiết với nhà cung cấp giúp vận hành tính năng, và với cơ quan có thẩm quyền khi pháp luật yêu cầu hoặc cho phép để bảo vệ người dùng, quyền lợi hợp pháp và an toàn. Chúng tôi không bán dữ liệu cá nhân để đổi lấy tiền.",
            ),
        ),
        PolicySection(
            title = "Nhà cung cấp dịch vụ và chuyển dữ liệu",
            paragraphs = listOf(
                "Một số tính năng sử dụng nhà cung cấp bên ngoài: dịch vụ bản đồ để tìm và hiển thị địa điểm; Firebase Cloud Messaging của Google để gửi thông báo đẩy; và Qwen qua Alibaba Cloud DashScope khi bạn chủ động yêu cầu tạo avatar AI. Khi dùng tính năng tương ứng, dữ liệu cần cho tính năng đó (chẳng hạn vị trí tìm kiếm, mã thông báo đẩy, hoặc ảnh và lời nhắc tạo avatar) có thể được gửi tới nhà cung cấp.",
                "Nhà cung cấp có thể xử lý dữ liệu tại quốc gia khác với nơi bạn sinh sống. Khi luật yêu cầu, chúng tôi áp dụng cơ chế và biện pháp bảo vệ phù hợp cho việc chuyển dữ liệu xuyên biên giới. Việc dùng dịch vụ bên thứ ba cũng chịu các điều khoản và chính sách riêng của nhà cung cấp đó.",
            ),
        ),
        PolicySection(
            title = "Thời hạn lưu trữ",
            paragraphs = listOf(
                "Chúng tôi lưu dữ liệu khi tài khoản còn hoạt động và trong thời gian cần thiết để cung cấp dịch vụ, thực hiện mục đích nêu trên, xử lý tranh chấp, bảo đảm an toàn hoặc tuân thủ pháp luật. Khi dữ liệu không còn cần thiết, chúng tôi xóa hoặc khử nhận dạng theo quy trình áp dụng. Một số bản sao lưu, nhật ký bảo mật hoặc hồ sơ cần giữ theo luật có thể tồn tại thêm trong thời hạn giới hạn.",
            ),
        ),
        PolicySection(
            title = "Quyền và lựa chọn của bạn",
            paragraphs = listOf(
                "Tùy luật áp dụng, bạn có thể yêu cầu biết, truy cập, sửa, xóa, hạn chế hoặc phản đối việc xử lý dữ liệu; nhận bản sao dữ liệu; rút lại sự đồng ý; và khiếu nại với cơ quan bảo vệ dữ liệu có thẩm quyền. Chúng tôi có thể cần xác minh danh tính và có thể từ chối hoặc giới hạn yêu cầu trong trường hợp luật cho phép. Gửi yêu cầu tới địa chỉ bên dưới; vui lòng không gửi mật khẩu hoặc giấy tờ nhạy cảm trừ khi chúng tôi hướng dẫn qua kênh an toàn.",
            ),
            contactLabel = "Gửi yêu cầu về dữ liệu: ",
        ),
        PolicySection(
            title = "Trẻ em và độ tuổi",
            paragraphs = listOf(
                "Saigon.Town không dành cho trẻ dưới 13 tuổi và chúng tôi không chủ ý thu thập dữ liệu của trẻ dưới độ tuổi tối thiểu được luật áp dụng cho phép. Nếu bạn chưa đủ tuổi hợp pháp tại nơi cư trú, đừng dùng dịch vụ nếu không có sự cho phép của cha mẹ hoặc người giám hộ khi luật yêu cầu. Ở Việt Nam, các quy định bảo vệ dữ liệu trẻ em và việc chấp thuận của cha mẹ/người giám hộ có thể áp dụng với người dưới 16 tuổi; tại Hoa Kỳ, COPPA có thể áp dụng với trẻ dưới 13 tuổi. Nếu phát hiện việc thu thập trái phép, hãy liên hệ để chúng tôi xem xét và xử lý phù hợp.",
            ),
        ),
        PolicySection(
            title = "Bảo mật và cập nhật",
            paragraphs = listOf(
                "Chúng tôi áp dụng biện pháp kỹ thuật và tổ chức phù hợp để hạn chế truy cập, mất mát hoặc sử dụng trái phép dữ liệu; không phương thức truyền hoặc lưu trữ nào có thể bảo đảm an toàn tuyệt đối. Chúng tôi có thể cập nhật chính sách này khi dịch vụ hoặc luật thay đổi. Bản cập nhật sẽ được đăng tại trang này cùng ngày hiệu lực; thay đổi quan trọng sẽ được thông báo theo cách phù hợp.",
                "Chính sách này được xây dựng theo Luật Bảo vệ dữ liệu cá nhân Việt Nam số 91/2025/QH15 và Nghị định 356/2025/NĐ-CP có hiệu lực từ ngày 1 tháng 1 năm 2026, cùng các quy định khác áp dụng cho bạn. Quyền theo GDPR được áp dụng khi GDPR điều chỉnh hoạt động xử lý của chúng tôi.",
            ),
        ),
    )
} else {
    listOf(
        PolicySection(
            title = "Who is responsible and what this covers",
            paragraphs = listOf(
                "This notice applies to Saigon.Town on the web and related services. Saigon.Town determines why and how personal data is processed to operate the service. Contact us with questions or requests about your data.",
            ),
            contactLabel = "Privacy contact: ",
        ),
        PolicySection(
            title = "Data we process",
            paragraphs = listOf("Depending on how you use the service, data may include:"),
            bullets = listOf(
                "Account and contact details: your Zalo-enabled phone number, account authentication information, display name, introduction, profile photo, and language.",
                "Content and communications: posts, photos, comments, messages, profile content, prompts, and photos you choose to use when creating an AI avatar.",
                "Location: places, coordinates, or check-in times that you choose to share, plus information needed to search for places and show maps.",
                "Technical data: cookies or browser storage needed for sessions, security, and language preferences; request information and visit statistics as needed to operate, protect, and improve the service.",
                "Push notifications: device or browser subscription tokens and delivery status if you enable web or app notifications.",
            ),
        ),
        PolicySection(
            title = "Why and on what grounds we process data",
            paragraphs = listOf(
                "We use data to create and secure accounts; show profiles, posts, messages, activities, and check-ins; provide maps, notifications, and features you request; support users; prevent fraud, abuse, and safety risks; meet legal obligations; and understand service performance and usage so we can maintain and improve the service.",
                "Depending on the applicable law and activity, our legal grounds may include providing the service you request, your consent, legitimate interests such as security and operations, or a legal obligation. Where processing is based on consent, you may withdraw it; withdrawal does not affect earlier processing or other lawful grounds.",
            ),
        ),
        PolicySection(
            title = "Visibility and sharing",
            paragraphs = listOf(
                "Some information you post or choose to share—such as your name, photo, posts, places, and check-ins—may be visible to other users as a feature works. Share only information you want the relevant recipients or community to see. Messages are provided to conversation participants; authorized staff may access them when necessary for support, safety, or enforcement.",
                "We share only the data needed with providers that help operate features, and with competent authorities when required or permitted by law to protect users, legal rights, or safety. We do not sell personal data for money.",
            ),
        ),
        PolicySection(
            title = "Service providers and international transfers",
            paragraphs = listOf(
                "Some features use third-party providers: mapping services to search for and show places; Google Firebase Cloud Messaging to deliver push notifications; and Qwen through Alibaba Cloud DashScope when you request AI avatar generation. When you use a relevant feature, data needed for it (for example a place search, push token, or avatar photo and prompt) may be sent to that provider.",
                "Providers may process data in a country different from yours. Where required by law, we use appropriate mechanisms and safeguards for cross-border transfers. Your use of a third-party feature is also subject to that provider’s own terms and privacy notice.",
            ),
        ),
        PolicySection(
            title = "How long we keep data",
            paragraphs = listOf(
                "We keep data while your account is active and for as long as needed to provide the service, fulfill the purposes above, resolve disputes, protect safety, or comply with law. When data is no longer needed, we delete or de-identify it under our applicable procedures. Limited backup copies, security logs, or records retained by law may remain for a limited period.",
            ),
        ),
        PolicySection(
            title = "Your rights and choices",
            paragraphs = listOf(
                "Depending on applicable law, you may request information about, access to, correction, deletion, or a copy of your data; ask us to restrict or stop certain processing; withdraw consent; and complain to a competent data protection authority. We may need to verify your identity and may refuse or limit a request where the law allows. Send a request to the address below; do not send passwords or sensitive identity documents unless we direct you through a secure channel.",
            ),
            contactLabel = "Data requests: ",
        ),
        PolicySection(
            title = "Children and age requirements",
            paragraphs = listOf(
                "Saigon.Town is not intended for children under 13, and we do not knowingly collect data from a child below the minimum age allowed by applicable law. If you are not old enough to use the service where you live, do not use it without parent or guardian authorization where required by law. Vietnam’s child-data protections and parental or guardian approval requirements may apply to people under 16; in the United States, COPPA may apply to children under 13. If you believe data was collected unlawfully, contact us so we can review and take appropriate action.",
            ),
        ),
        PolicySection(
            title = "Security and changes to this notice",
            paragraphs = listOf(
                "We use appropriate technical and organizational measures to reduce the risk of unauthorized access, loss, or misuse; no method of transmission or storage can be guaranteed completely secure. We may update this notice when the service or law changes. The revised notice and effective date will appear here; we will communicate material changes in an appropriate way.",
                "This notice is designed with Vietnam’s Personal Data Protection Law No. 91/2025/QH15 and Decree No. 356/2025/ND-CP, effective 1 January 2026, and other laws applicable to you in mind. GDPR rights apply where the GDPR governs our processing.",
            ),
        ),
    )
}

private fun csaeSections(): List<PolicySection> = if (Town.language == Language.VI) {
    listOf(
        PolicySection(
            title = "Cam kết an toàn trẻ em và không khoan nhượng CSAE",
            paragraphs = listOf(
                "Saigon.Town nghiêm cấm mọi hành vi xâm hại và bóc lột tình dục trẻ em (CSAE), cũng như mọi tài liệu xâm hại tình dục trẻ em (CSAM). Chính sách này áp dụng với mọi người dùng, nội dung, hồ sơ, bài đăng, ảnh, bình luận và tin nhắn trên Saigon.Town. Trong chính sách này, trẻ em là người dưới 18 tuổi, bất kể luật địa phương định nghĩa trẻ em như thế nào.",
            ),
        ),
        PolicySection(
            title = "Hành vi và nội dung bị cấm",
            paragraphs = listOf("Không được tạo, tải lên, chia sẻ, yêu cầu, quảng bá hoặc giúp người khác tìm thấy nội dung hay hành vi sau:"),
            bullets = listOf(
                "CSAM dưới mọi hình thức, bao gồm ảnh, video, phát trực tiếp, liên kết hoặc nội dung tổng hợp bằng AI/giả mạo có mô tả tình dục trẻ em hoặc người trông như trẻ em.",
                "Dụ dỗ, tiếp cận để khai thác tình dục, gạ gẫm, lôi kéo trẻ em, trao đổi ảnh nhạy cảm, hoặc hướng dẫn trẻ em chuyển sang kênh riêng hay gặp trực tiếp vì mục đích tình dục.",
                "Tống tiền tình dục, cưỡng ép, đe dọa, buôn bán người, bóc lột tình dục thương mại, môi giới hoặc hỗ trợ hành vi xâm hại trẻ em.",
                "Tình dục hóa, khai thác hoặc cổ súy xâm hại trẻ em; hướng dẫn cách tạo, che giấu, lưu trữ hay phát tán CSAM; hoặc tìm cách né tránh biện pháp an toàn và báo cáo.",
            ),
        ),
        PolicySection(
            title = "Cách báo cáo",
            paragraphs = listOf(
                "Báo cáo nội dung hoặc hành vi nghi ngờ càng sớm càng tốt qua email. Ghi rõ “Child Safety / CSAE” trong tiêu đề và cung cấp liên kết hoặc mã nội dung/hồ sơ, tên tài khoản, thời điểm và mô tả ngắn để chúng tôi có thể tìm và xem xét. Đừng tải xuống, chụp lại, đính kèm hoặc gửi tiếp hình ảnh/video nghi vấn; chỉ gửi thông tin định vị. Email không được giám sát liên tục và không thay thế dịch vụ khẩn cấp.",
                "Nếu trẻ em đang gặp nguy hiểm tức thời, hãy liên hệ ngay cơ quan khẩn cấp hoặc cơ quan thực thi pháp luật tại nơi bạn ở. Tại Hoa Kỳ, công chúng có thể báo cáo trực tiếp qua NCMEC CyberTipline: https://report.cybertip.org. Hãy báo cho cơ quan bảo vệ trẻ em hoặc cơ quan có thẩm quyền tại địa phương khi phù hợp.",
            ),
            contactLabel = "Đầu mối an toàn trẻ em của Saigon.Town: ",
        ),
        PolicySection(
            title = "Xem xét và xử lý báo cáo",
            paragraphs = listOf(
                "Chúng tôi ưu tiên báo cáo liên quan đến trẻ em và xem xét thông tin có sẵn nhanh nhất có thể. Khi xác định có vi phạm, chúng tôi sẽ gỡ hoặc vô hiệu hóa nội dung, hạn chế hoặc chấm dứt tài khoản liên quan—có thể cấm vĩnh viễn—và thực hiện bước bảo vệ cần thiết. Khi có căn cứ và pháp luật cho phép hoặc yêu cầu, chúng tôi có thể bảo toàn hồ sơ liên quan với quyền truy cập hạn chế và cung cấp thông tin cho cơ quan thực thi pháp luật hoặc cơ quan bảo vệ trẻ em có thẩm quyền.",
                "Báo cáo thiện chí sẽ được xem xét. Không được trả đũa người báo cáo, nạn nhân hoặc người hỗ trợ trẻ em. Cố ý gửi báo cáo sai để quấy rối người khác cũng có thể dẫn đến hạn chế hoặc chấm dứt tài khoản.",
            ),
        ),
        PolicySection(
            title = "Tuân thủ và cập nhật",
            paragraphs = listOf(
                "Chúng tôi áp dụng chính sách này cùng luật bảo vệ trẻ em và báo cáo CSAM áp dụng tại từng nơi. Khi pháp luật yêu cầu hoặc phù hợp, chúng tôi chuyển báo cáo đã xác nhận tới NCMEC CyberTipline hoặc cơ quan khu vực có thẩm quyền. Chính sách này có hiệu lực từ ngày 20 tháng 11 năm 2026 và có thể được cập nhật khi quy định hoặc biện pháp an toàn thay đổi.",
            ),
        ),
    )
} else {
    listOf(
        PolicySection(
            title = "Child safety commitment and zero tolerance for CSAE",
            paragraphs = listOf(
                "Saigon.Town strictly prohibits all child sexual abuse and exploitation (CSAE), and all child sexual abuse material (CSAM). This policy applies to every user and all profiles, posts, photos, comments, and messages on Saigon.Town. For this policy, a child is anyone under 18, regardless of a local legal definition.",
            ),
        ),
        PolicySection(
            title = "Prohibited conduct and content",
            paragraphs = listOf("You may not create, upload, share, solicit, promote, or help anyone find any of the following:"),
            bullets = listOf(
                "CSAM in any form, including images, video, livestreams, links, or AI-generated/manipulated content that sexually depicts a child or someone who appears to be a child.",
                "Grooming, sexual enticement, solicitation, luring, exchanging sexual images, or directing a child to move to a private channel or meet in person for a sexual purpose.",
                "Sextortion, coercion, threats, trafficking, commercial sexual exploitation, arranging, or facilitating the abuse of a child.",
                "Sexualizing, exploiting, or glorifying child abuse; instructions to create, conceal, store, or distribute CSAM; or attempts to evade safety and reporting measures.",
            ),
        ),
        PolicySection(
            title = "How to report",
            paragraphs = listOf(
                "Report suspected content or conduct promptly by email. Put “Child Safety / CSAE” in the subject and include the content or profile URL/ID, account name, date or time, and a short description so we can locate and review it. Do not download, screenshot, attach, or forward suspected imagery or video; send locating information only. Email is not monitored continuously and is not an emergency service.",
                "If a child faces immediate danger, contact local emergency services or law enforcement now. In the United States, members of the public can report directly to the NCMEC CyberTipline at https://report.cybertip.org. Contact the relevant local child-protection or law-enforcement authority where appropriate.",
            ),
            contactLabel = "Saigon.Town Child Safety Point of Contact: ",
        ),
        PolicySection(
            title = "Review and enforcement",
            paragraphs = listOf(
                "We prioritize child-safety reports and review available information as quickly as practicable. When we determine a violation occurred, we will remove or disable the content, restrict or terminate the associated account—which may include a permanent ban—and take necessary protective steps. Where there is a basis and the law permits or requires it, we may preserve relevant records with restricted access and provide information to competent law-enforcement or child-protection authorities.",
                "Good-faith reports will be reviewed. Retaliation against a reporter, a victim, or someone helping a child is prohibited. Knowingly making a false report to harass someone may also result in account restrictions or termination.",
            ),
        ),
        PolicySection(
            title = "Legal compliance and updates",
            paragraphs = listOf(
                "We apply this policy alongside child-safety and CSAM-reporting laws that apply in each relevant jurisdiction. Where required by law or appropriate, we refer confirmed reports to the NCMEC CyberTipline or the relevant regional authority. This policy is effective 20 November 2026 and may be updated as laws or safety practices change.",
            ),
        ),
    )
}
