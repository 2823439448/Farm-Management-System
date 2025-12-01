// 注册处理函数 - 现在负责发送 AJAX 请求到后端
async function handleRegister(event) {
    // 阻止表单默认提交行为
    if (event) {
        event.preventDefault();
    }

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirm-password').value;

    // 1. 检查所有字段是否填写
    if (!username || !password || !confirmPassword) {
        alert('请填写所有字段（用户名、密码、确认密码）！');
        return false;
    }

    // 2. 检查密码是否一致
    if (password !== confirmPassword) {
        alert('错误：两次输入的密码不一致！');
        // 清空密码字段
        document.getElementById('password').value = '';
        document.getElementById('confirm-password').value = '';
        return false;
    }

    // 3. 执行注册逻辑 - 发送 Fetch API 请求到后端
    try {
        const response = await fetch('/register', { // 目标URL：/register
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ // 发送 JSON 数据
                username: username,
                password: password // 注意：实际应用中密码应加密传输和存储
            })
        });

        // 检查HTTP状态码
        if (response.ok) {
            // 注册成功
            alert(`恭喜您，注册成功！用户名: ${username}`);
            // 成功后跳转到登录页面
            window.location.href = '/login/login.html';
            return true;
        } else {
            // 注册失败 (如用户名已存在或后端错误)
            const errorData = await response.json();
            alert(`注册失败: ${errorData.message || '未知错误'}`);
            return false;
        }
    } catch (error) {
        console.error('注册请求发送失败:', error);
        alert('网络或系统错误，请稍后再试。');
        return false;
    }
}

// 🎯 修改点：将“登录”按钮的处理函数改为调用 handleRegister
function handleLoginRedirect(event) {
    // 调用注册处理函数，实现点击“登录”按钮也触发注册逻辑
    handleRegister(event);
}


// 添加事件监听器
document.addEventListener('DOMContentLoaded', function() {
    // 绑定“完成”按钮（注册）
    document.getElementById('register-button').addEventListener('click', handleRegister);

    // 绑定“登录”按钮：现在也调用 handleRegister
    document.getElementById('login-button').addEventListener('click', handleLoginRedirect);

    // 绑定回车键事件到表单，使其默认触发注册
    document.getElementById('register-form').addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
            event.preventDefault(); // 阻止表单默认提交行为
            handleRegister(event);
        }
    });
});