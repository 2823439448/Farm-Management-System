function handleLogin() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    if (!username || !password) {
        alert('请输入用户名和密码！');
        return;
    }

    const loginUrl = '/login';

    fetch(loginUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            username: username,
            password: password
        })
    })
        .then(response => {
            if (response.ok) {
                return response.json();
            } else {
                return response.json().then(error => {
                    throw new Error(error.message || `登录失败: ${response.status}`);
                });
            }
        })
        .then(data => {
            // 登录成功后的逻辑
            if (data.userId) {
                // 直接进入主页面，不再去后端检查是否绑定了设备
                window.location.href = "/index/index.html";
            } else {
                alert('登录失败，服务器返回数据异常。');
            }
        })
        .catch(error => {
            console.error('登录请求出错:', error);
            alert(`❌ 登录失败！${error.message}`);
        });
}

// 回车键监听
document.getElementById('login-form').addEventListener('keydown', function(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        handleLogin();
    }
});