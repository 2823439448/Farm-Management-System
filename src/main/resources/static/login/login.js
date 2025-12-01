// 登录处理函数
function handleLogin() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    // 检查用户名和密码是否输入
    if (!username || !password) {
        alert('请输入用户名和密码！');
        return;
    }

    // 假设后端接口地址是 /login
    const loginUrl = '/login';

    // 禁用按钮/显示加载状态（可选，此处省略）
    // const loginButton = document.getElementById('login-btn');
    // loginButton.disabled = true;

    // 使用 fetch API 发送 POST 请求
    fetch(loginUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        // 将用户名和密码作为 JSON 格式发送
        body: JSON.stringify({
            username: username,
            password: password
        })
    })
        .then(response => {
            // 重新启用按钮
            // loginButton.disabled = false;

            // 检查 HTTP 状态码
            if (response.ok) {
                // 登录成功，解析 JSON 响应
                return response.json();
            } else {
                // 登录失败，根据状态码或错误信息进行处理
                // 抛出错误，进入 catch 块
                return response.json().then(error => {
                    throw new Error(error.message || `登录失败，HTTP 状态码: ${response.status}`);
                });
            }
        })
        .then(data => {
            // 假设后端返回的数据包含 userId，例如 { success: true, userId: "user123" }
            if (data.userId) {
                // 登录成功，显示用户 ID 弹窗
                //alert(`✅ 登录成功！用户 ID: ${data.userId}`);

                // 登录成功后，您可以在此处执行跳转到主页的操作
                window.location.href = "/index/index.html";
            } else {
                // 即使状态码是 200，但数据结构不符合预期，也视为失败
                alert('登录失败，服务器返回数据异常。');
            }
        })
        .catch(error => {
            // 处理网络错误、JSON 解析错误或自定义抛出的错误
            console.error('登录请求出错:', error);
            alert(`❌ 登录失败！错误信息: ${error.message || '网络连接失败或服务器无响应'}`);
        });
}

// 键盘回车事件 (只保留回车确认)
document.getElementById('login-form').addEventListener('keydown', function(event) {
    if (event.key === 'Enter') {
        event.preventDefault(); // 阻止表单默认提交行为
        handleLogin(); // 调用新的登录处理函数
    }
});

// 您还需要确保您的登录按钮（例如 ID 为 'login-btn'）也调用 handleLogin 函数
// 例如，在 HTML 中添加 onclick="handleLogin()" 或在 JS 中添加事件监听器:
/* document.getElementById('login-btn').addEventListener('click', handleLogin);
*/