// 登录处理函数
function handleLogin() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    if (username && password) {
        // 使用原始的 alert 逻辑
        alert(`尝试登录...\n用户名: ${username}\n密码: ${password}`);
    } else {
        alert('请输入用户名和密码！');
    }
}

// 键盘回车事件 (只保留回车确认)
document.getElementById('login-form').addEventListener('keydown', function(event) {
    if (event.key === 'Enter') {
        event.preventDefault(); // 阻止表单默认提交行为
        handleLogin();
    }
});